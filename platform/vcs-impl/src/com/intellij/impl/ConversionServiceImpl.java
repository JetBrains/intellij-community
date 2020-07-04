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
import com.intellij.project.ProjectStoreOwner;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class ConversionServiceImpl extends ConversionService {
  private static final Logger LOG = Logger.getInstance(ConversionServiceImpl.class);

  @NotNull
  @Override
  public ConversionResult convertSilently(@NotNull Path projectPath, @NotNull ConversionListener listener) {
    try {
      ConversionContextImpl context = new ConversionContextImpl(projectPath);
      List<ConversionRunner> runners = isConversionNeeded(context);
      if (runners.isEmpty()) {
        return ConversionResultImpl.CONVERSION_NOT_NEEDED;
      }

      listener.conversionNeeded();

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

  @Override
  public @NotNull ConversionResult convert(@NotNull Path projectPath) throws CannotConvertException {
    if (ApplicationManager.getApplication().isHeadlessEnvironment() || !ConverterProvider.EP_NAME.hasAnyExtensions() || !Files.exists(projectPath)) {
      return ConversionResultImpl.CONVERSION_NOT_NEEDED;
    }

    ConversionContextImpl context = new ConversionContextImpl(projectPath);
    List<ConversionRunner> converters = isConversionNeeded(context);
    if (converters.isEmpty()) {
      return ConversionResultImpl.CONVERSION_NOT_NEEDED;
    }

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

  private static @NotNull List<ConversionRunner> isConversionNeeded(@NotNull ConversionContextImpl context) {
    try {
      Object2LongMap<String> oldMap = context.getProjectFileTimestamps();
      boolean changed = false;
      if (oldMap.isEmpty()) {
        LOG.debug("conversion will be performed because no information about project files");
      }
      else {
        Object2LongMap<String> newMap = context.getAllProjectFiles();
        LOG.debug("Checking project files");
        for (ObjectIterator<Object2LongMap.Entry<String>> iterator = Object2LongMaps.fastIterator(newMap); iterator.hasNext(); ) {
          Object2LongMap.Entry<String> entry = iterator.next();
          String path = entry.getKey();
          long oldValue = oldMap.getLong(path);
          long newValue = entry.getLongValue();
          if (newValue != oldValue) {
            LOG.info("conversion will be performed because at least " + path + " is changed (oldLastModified=" + oldValue + ", newLastModified=" + newValue + ")");
            changed = true;
            break;
          }
        }
      }

      Set<String> performedConversionIds;
      if (changed) {
        performedConversionIds = Collections.emptySet();
      }
      else {
        performedConversionIds = context.getAppliedConverters();
        if (LOG.isDebugEnabled()) {
          LOG.debug("Project files are up to date. Applied converters: " + performedConversionIds);
        }
      }

      List<ConversionRunner> runners = new ArrayList<>();
      for (ConverterProvider provider : ConverterProvider.EP_NAME.getExtensionList()) {
        if (!performedConversionIds.contains(provider.getId())) {
          ConversionRunner runner = new ConversionRunner(provider, context);
          if (runner.isConversionNeeded()) {
            runners.add(runner);
          }
        }
      }
      return runners;
    }
    catch (CannotConvertException e) {
      LOG.info("Cannot check whether conversion of project files is needed or not, conversion won't be performed", e);
      return Collections.emptyList();
    }
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
  public @NotNull ConversionResult convertModule(@NotNull Project project, @NotNull Path moduleFile) {
    ConversionContextImpl context = new ConversionContextImpl(((ProjectStoreOwner)project).getComponentStore().getProjectFilePath());

    List<ConversionRunner> runners = new ArrayList<>();
    try {
      for (ConverterProvider provider : ConverterProvider.EP_NAME.getExtensionList()) {
        ConversionRunner runner = new ConversionRunner(provider, context);
        if (runner.isModuleConversionNeeded(moduleFile)) {
          runners.add(runner);
        }
      }
    }
    catch (CannotConvertException e) {
      LOG.info(e);
    }

    if (runners.isEmpty()) {
      return ConversionResultImpl.CONVERSION_NOT_NEEDED;
    }

    int answer = Messages.showYesNoDialog(project, IdeBundle.message("message.module.file.has.an.older.format.do.you.want.to.convert.it"),
                                       IdeBundle.message("dialog.title.convert.module"), Messages.getQuestionIcon());
    if (answer != Messages.YES) {
      return ConversionResultImpl.CONVERSION_CANCELED;
    }

    if (!Files.isWritable(moduleFile)) {
      Messages.showErrorDialog(project, IdeBundle.message("error.message.cannot.modify.file.0", moduleFile.toAbsolutePath().toString()),
                               IdeBundle.message("dialog.title.convert.module"));
      return ConversionResultImpl.ERROR_OCCURRED;
    }

    try {
      File backupFile = ProjectConversionUtil.backupFile(moduleFile);
      for (ConversionRunner runner : runners) {
        runner.convertModule(moduleFile);
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
}
