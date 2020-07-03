// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.impl;

import com.intellij.conversion.*;
import com.intellij.conversion.impl.ConversionContextImpl;
import com.intellij.conversion.impl.ConversionRunner;
import com.intellij.conversion.impl.ProjectConversionUtil;
import com.intellij.conversion.impl.ui.ConvertProjectDialog;
import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.containers.ObjectLongHashMap;
import com.intellij.util.graph.*;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.jdom.Element;
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
      saveConversionResult(context);
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
      saveConversionResult(context);
    }
    return ref.get();
  }

  @NotNull
  private static List<ConversionRunner> getConversionRunners(@NotNull ConversionContextImpl context) throws CannotConvertException {
    final List<ConversionRunner> converters = getSortedConverters(context);
    final Iterator<ConversionRunner> iterator = converters.iterator();

    Set<String> convertersToRunIds = new HashSet<>();
    while (iterator.hasNext()) {
      ConversionRunner runner = iterator.next();
      boolean conversionNeeded = runner.isConversionNeeded();
      if (!conversionNeeded) {
        for (String id : runner.getProvider().getPrecedingConverterIds()) {
          if (convertersToRunIds.contains(id)) {
            conversionNeeded = true;
            break;
          }
        }
      }

      if (conversionNeeded) {
        convertersToRunIds.add(runner.getProvider().getId());
      }
      else {
        iterator.remove();
      }
    }
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
      saveConversionResult(context);
    }
    catch (CannotConvertException e) {
      LOG.info("Cannot check whether conversion of project files is needed or not, conversion won't be performed", e);
    }
    return false;
  }

  private static @NotNull List<ConversionRunner> getSortedConverters(@NotNull ConversionContextImpl context) throws CannotConvertException {
    Object2LongMap<String> oldMap = context.getProjectFileTimestamps();
    ObjectLongHashMap<String> newMap = getProjectFilesMap(context);
    LOG.debug("Checking project files");
    boolean changed;
    if (LOG.isDebugEnabled()) {
      Ref<Boolean> changedRef = new Ref<>(false);
      newMap.forEachEntry((path, newValue) -> {
        long oldValue = oldMap.getLong(path);
        if (oldValue == -1) {
          LOG.debug(" new file: " + path);
          changedRef.set(true);
        }
        else if (newValue != oldValue) {
          LOG.debug(" changed file: " + path);
          changedRef.set(true);
        }
        return true;
      });
      changed = changedRef.get();
    }
    else {
      // if debug log is not enabled, do not process all entries
      changed = !newMap.forEachEntry((path, newValue) -> {
        long oldValue = oldMap.getLong(path);
        boolean isFileChangedOrNew = newValue != oldValue;
        if (isFileChangedOrNew) {
          LOG.info("conversion will be performed because at least " + path + " is changed (oldLastModified=" + oldValue + ", newLastModified=" + newValue);
        }
        // continue to process only file is not changed
        return !isFileChangedOrNew;
      });
    }

    final Set<String> performedConversionIds;
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
  private static ObjectLongHashMap<String> getProjectFilesMap(@NotNull ConversionContextImpl context) throws CannotConvertException {
    Activity activity = StartUpMeasurer.startActivity("conversion: project files collecting");
    ObjectLongHashMap<String> result = context.getAllProjectFiles();
    activity.end();
    return result;
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

    if (runners.isEmpty()) {
      return runners;
    }

    final Graph<ConverterProvider> graph = GraphGenerator.generate(CachingSemiGraph.cache(new ConverterProvidersGraph(providers)));
    final DFSTBuilder<ConverterProvider> builder = new DFSTBuilder<>(graph);
    if (!builder.isAcyclic()) {
      Pair<ConverterProvider, ConverterProvider> pair = builder.getCircularDependency();
      LOG.error("cyclic dependencies between converters: " + Objects.requireNonNull(pair).getFirst().getId() + " and " + pair.getSecond().getId());
    }
    final Comparator<ConverterProvider> comparator = builder.comparator();
    runners.sort((o1, o2) -> comparator.compare(o1.getProvider(), o2.getProvider()));
    return runners;
  }

  @Override
  public void saveConversionResult(@NotNull Path projectPath) {
    try {
      saveConversionResult(new ConversionContextImpl(projectPath));
    }
    catch (CannotConvertException e) {
      LOG.info(e);
    }
  }

  private static void saveConversionResult(@NotNull ConversionContextImpl context) throws CannotConvertException {
    Element root = new Element("conversion");
    Element appliedConverters = new Element("applied-converters");
    root.addContent(appliedConverters);
    for (ConverterProvider provider : ConverterProvider.EP_NAME.getExtensionList()) {
      appliedConverters.addContent(new Element("converter").setAttribute("id", provider.getId()));
    }

    Element projectFiles = new Element("project-files");
    root.addContent(projectFiles);

    ObjectLongHashMap<String> projectFilesMap = getProjectFilesMap(context);
    projectFilesMap.forEachEntry((key, value) -> {
      projectFiles.addContent(new Element("file").setAttribute("path", key).setAttribute("timestamp", String.valueOf(value)));
      return true;
    });

    Path infoFile = context.getConversionInfoFile();
    try {
      JDOMUtil.write(root, infoFile);
    }
    catch (IOException e) {
      LOG.error(e);
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

  private static final class ConverterProvidersGraph implements InboundSemiGraph<ConverterProvider> {
    private final List<ConverterProvider> myProviders;

    ConverterProvidersGraph(@NotNull List<ConverterProvider> providers) {
      myProviders = providers;
    }

    @NotNull
    @Override
    public Collection<ConverterProvider> getNodes() {
      return myProviders;
    }

    @NotNull
    @Override
    public Iterator<ConverterProvider> getIn(ConverterProvider n) {
      List<ConverterProvider> preceding = new ArrayList<>();
      for (String id : n.getPrecedingConverterIds()) {
        for (ConverterProvider provider : myProviders) {
          if (provider.getId().equals(id)) {
            preceding.add(provider);
          }
        }
      }
      return preceding.iterator();
    }
  }
}
