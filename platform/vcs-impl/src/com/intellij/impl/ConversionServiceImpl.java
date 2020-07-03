// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.impl;

import com.intellij.conversion.*;
import com.intellij.conversion.impl.ConversionContextImpl;
import com.intellij.conversion.impl.ConversionRunner;
import com.intellij.conversion.impl.ProjectConversionUtil;
import com.intellij.conversion.impl.ui.ConvertProjectDialog;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsBundle;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public final class ConversionServiceImpl extends ConversionService {
  private static final Logger LOG = Logger.getInstance(ConversionServiceImpl.class);

  @NotNull
  @Override
  public ConversionResult convertSilently(@NotNull Path projectPath, @NotNull ConversionListener listener) {
    try {
      ConversionContextImpl context = new ConversionContextImpl(projectPath);
      if (!isConversionNeeded(context)) {
        return ConversionResultImpl.CONVERSION_NOT_NEEDED;
      }

      listener.conversionNeeded();
      List<ConversionRunner> runners = getConversionRunners(context);

      Set<Path> affectedFiles = new HashSet<>();
      for (ConversionRunner runner : runners) {
        runner.collectAffectedFiles(affectedFiles);
      }

      List<Path> readOnlyFiles = ConversionRunner.getReadOnlyFiles(affectedFiles);
      if (!readOnlyFiles.isEmpty()) {
        listener.cannotWriteToFiles(readOnlyFiles);
        return ConversionResultImpl.ERROR_OCCURRED;
      }
      Path backupDir = ProjectConversionUtil.backupFiles(affectedFiles, context.getProjectBaseDir());
      for (ConversionRunner runner : runners) {
        if (runner.isConversionNeeded()) {
          runner.preProcess();
          runner.process();
          runner.postProcess();
        }
      }
      context.saveFiles(affectedFiles);
      listener.successfullyConverted(backupDir);
      context.saveConversionResult();
      return new ConversionResultImpl(runners);
    }
    catch (CannotConvertException | IOException e) {
      listener.error(e.getMessage());
    }
    return ConversionResultImpl.ERROR_OCCURRED;
  }

  @NotNull
  @Override
  public ConversionResult convert(@NotNull Path projectPath) throws CannotConvertException {
    if (ApplicationManager.getApplication().isHeadlessEnvironment() || !ConverterProvider.EP_NAME.hasAnyExtensions() || !Files.exists(projectPath)) {
      return ConversionResultImpl.CONVERSION_NOT_NEEDED;
    }

    ConversionContextImpl context = new ConversionContextImpl(projectPath);
    if (!isConversionNeeded(context)) {
      return ConversionResultImpl.CONVERSION_NOT_NEEDED;
    }

    List<ConversionRunner> converters = getConversionRunners(context);
    Ref<ConversionResult> ref = new Ref<>(ConversionResultImpl.CONVERSION_CANCELED);
    ApplicationManager.getApplication().invokeAndWait(() -> {
      ConvertProjectDialog dialog = new ConvertProjectDialog(context, converters);
      dialog.show();
      if (dialog.isConverted()) {
        ref.set(new ConversionResultImpl(converters));
      }
    });

    if (!ref.isNull()) {
      try {
        context.saveConversionResult();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return ref.get();
  }

  private static @NotNull List<ConversionRunner> getConversionRunners(@NotNull ConversionContextImpl context) throws CannotConvertException {
    List<ConversionRunner> converters = getSortedConverters(context);
    converters.removeIf(runner -> !runner.isConversionNeeded());
    return converters;
  }

  private static boolean isConversionNeeded(@NotNull ConversionContextImpl context) {
    try {
      List<ConversionRunner> runners = getSortedConverters(context);
      if (runners.isEmpty()) {
        return false;
      }
      for (ConversionRunner runner : runners) {
        if (runner.isConversionNeeded()) {
          return true;
        }
      }
      context.saveConversionResult();
    }
    catch (IOException e) {
      LOG.info(e);
    }
    catch (CannotConvertException e) {
      LOG.info("Cannot check whether conversion of project files is needed or not, conversion won't be performed", e);
    }
    return false;
  }

  private static @NotNull List<ConversionRunner> getSortedConverters(@NotNull ConversionContextImpl context) throws CannotConvertException {
    Object2LongMap<String> oldMap = context.getProjectFileTimestamps();
    Object2LongMap<String> newMap = context.getAllProjectFiles();
    LOG.debug("Checking project files");
    boolean changed = false;
    if (LOG.isDebugEnabled()) {
      for (ObjectIterator<Object2LongMap.Entry<String>> iterator = Object2LongMaps.fastIterator(newMap); iterator.hasNext(); ) {
        Object2LongMap.Entry<String> entry = iterator.next();
        String path = entry.getKey();
        long oldValue = oldMap.getLong(path);
        if (oldValue <= 0) {
          LOG.debug(" new file: " + path);
          changed = true;
        }
        else if (entry.getLongValue() != oldValue) {
          LOG.debug(" changed file: " + path);
          changed = true;
        }
      }
    }
    else {
      // if debug log is not enabled, do not process all entries
      for (ObjectIterator<Object2LongMap.Entry<String>> iterator = Object2LongMaps.fastIterator(newMap); iterator.hasNext(); ) {
        Object2LongMap.Entry<String> entry = iterator.next();
        String path = entry.getKey();
        long oldValue = oldMap.getLong(path);
        long newValue = entry.getLongValue();
        if (newValue != oldValue) {
          LOG.info("conversion will be performed because at least " + path + " is changed (oldLastModified=" + oldValue + ", newLastModified=" + newValue);
          changed = true;
          break;
        }
      }
    }

    Set<String> performedConversionIds;
    if (changed) {
      performedConversionIds = Collections.emptySet();
      LOG.debug("Project files were modified.");
    }
    else {
      performedConversionIds = context.getAppliedConverters();
      LOG.debug("Project files are up to date. Applied converters: " + performedConversionIds);
    }
    return createConversionRunners(context, performedConversionIds);
  }

  @NotNull
  private static List<ConversionRunner> createConversionRunners(@NotNull ConversionContextImpl context, @NotNull Set<String> performedConversionIds) {
    List<ConversionRunner> runners = new ArrayList<>();
    List<ConverterProvider> providers = ConverterProvider.EP_NAME.getExtensionList();
    for (ConverterProvider provider : providers) {
      if (!performedConversionIds.contains(provider.getId())) {
        runners.add(new ConversionRunner(provider, context));
      }
    }
    return runners;
  }

  @Override
  public void saveConversionResult(@NotNull Path projectPath) {
    try {
      new ConversionContextImpl(projectPath).saveConversionResult();
    }
    catch (IOException e) {
      LOG.error(e);
    }
    catch (CannotConvertException e) {
      LOG.info(e);
    }
  }

  @Override
  @NotNull
  public ConversionResult convertModule(@NotNull final Project project, @NotNull final Path moduleFile) {
    final String url = project.getPresentableUrl();
    assert url != null : project;
    final Path projectPath = Paths.get(url);

    if (!isConversionNeeded(projectPath, moduleFile)) {
      return ConversionResultImpl.CONVERSION_NOT_NEEDED;
    }

    final int res = Messages.showYesNoDialog(project, IdeBundle.message("message.module.file.has.an.older.format.do.you.want.to.convert.it"),
                                             IdeBundle.message("dialog.title.convert.module"), Messages.getQuestionIcon());
    if (res != Messages.YES) {
      return ConversionResultImpl.CONVERSION_CANCELED;
    }
    if (!Files.isWritable(moduleFile)) {
      Messages.showErrorDialog(project, IdeBundle.message("error.message.cannot.modify.file.0", moduleFile.toAbsolutePath().toString()),
                               IdeBundle.message("dialog.title.convert.module"));
      return ConversionResultImpl.ERROR_OCCURRED;
    }

    try {
      ConversionContextImpl context = new ConversionContextImpl(projectPath);
      List<ConversionRunner> runners = createConversionRunners(context, Collections.emptySet());
      File backupFile = ProjectConversionUtil.backupFile(moduleFile);
      for (ConversionRunner runner : runners) {
        if (runner.isModuleConversionNeeded(moduleFile)) {
          runner.convertModule(moduleFile);
        }
      }
      context.saveFiles(Collections.singletonList(moduleFile));
      Messages.showInfoMessage(project, IdeBundle.message("message.your.module.was.successfully.converted.br.old.version.was.saved.to.0", backupFile.getAbsolutePath()),
                               IdeBundle.message("dialog.title.convert.module"));
      return new ConversionResultImpl(runners);
    }
    catch (CannotConvertException e) {
      LOG.info(e);
      Messages.showErrorDialog(IdeBundle.message("error.cannot.load.project", e.getMessage()),
                               VcsBundle.message("dialog.title.cannot.convert.module"));
      return ConversionResultImpl.ERROR_OCCURRED;
    }
    catch (IOException e) {
      LOG.info(e);
      return ConversionResultImpl.ERROR_OCCURRED;
    }
  }

  private static boolean isConversionNeeded(Path projectPath, Path moduleFile) {
    try {
      ConversionContextImpl context = new ConversionContextImpl(projectPath);
      final List<ConversionRunner> runners = createConversionRunners(context, Collections.emptySet());
      for (ConversionRunner runner : runners) {
        if (runner.isModuleConversionNeeded(moduleFile)) {
          return true;
        }
      }
      return false;
    }
    catch (CannotConvertException e) {
      LOG.info(e);
      return false;
    }
  }
}
