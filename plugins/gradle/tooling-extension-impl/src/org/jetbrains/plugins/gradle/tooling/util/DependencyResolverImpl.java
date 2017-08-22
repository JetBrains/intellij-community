/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.jetbrains.plugins.gradle.tooling.util;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import groovy.lang.MetaMethod;
import groovy.lang.MetaProperty;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.component.*;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.result.*;
import org.gradle.api.component.Artifact;
import org.gradle.api.component.Component;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.AbstractCopyTask;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.java.artifact.JavadocArtifact;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.model.ExternalDependency;
import org.jetbrains.plugins.gradle.model.FileCollectionDependency;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Predicates.isNull;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.filter;
import static org.codehaus.groovy.runtime.StringGroovyMethods.capitalize;

/**
 * @author Vladislav.Soroka
 * @since 8/19/2015
 */
public class DependencyResolverImpl implements DependencyResolver {

  private static boolean is4OrBetter = GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("4.0")) >= 0;
  private static boolean isJavaLibraryPluginSupported = is4OrBetter ||
                                                        (GradleVersion.current().compareTo(GradleVersion.version("3.4")) >= 0);
  private static boolean isDependencySubstitutionsSupported = isJavaLibraryPluginSupported ||
                                                              (GradleVersion.current().compareTo(GradleVersion.version("2.5")) > 0);
  private static boolean isArtifactResolutionQuerySupported = isDependencySubstitutionsSupported ||
                                                              (GradleVersion.current().compareTo(GradleVersion.version("2.0")) >= 0);

  @NotNull
  private final Project myProject;
  private final boolean myIsPreview;
  private final boolean myDownloadJavadoc;
  private final boolean myDownloadSources;
  private final SourceSetCachedFinder mySourceSetFinder;

  @SuppressWarnings("GroovyUnusedDeclaration")
  public DependencyResolverImpl(@NotNull Project project, boolean isPreview) {
    myProject = project;
    myIsPreview = isPreview;
    myDownloadJavadoc = false;
    myDownloadSources = false;
    mySourceSetFinder = new SourceSetCachedFinder(project);
  }

  public DependencyResolverImpl(
    @NotNull Project project,
    boolean isPreview,
    boolean downloadJavadoc,
    boolean downloadSources,
    SourceSetCachedFinder sourceSetFinder) {
    myProject = project;
    myIsPreview = isPreview;
    myDownloadJavadoc = downloadJavadoc;
    myDownloadSources = downloadSources;
    mySourceSetFinder = sourceSetFinder;
  }

  @Override
  public Collection<ExternalDependency> resolveDependencies(@Nullable String configurationName) {
    return resolveDependencies(configurationName, null);
  }

  Collection<ExternalDependency> resolveDependencies(@Nullable String configurationName, @Nullable String scope) {
    if (configurationName == null) return Collections.emptyList();
    return resolveDependencies(myProject.getConfigurations().findByName(configurationName), scope).getExternalDeps();
  }

  @Override
  public Collection<ExternalDependency> resolveDependencies(@Nullable Configuration configuration) {
    return resolveDependencies(configuration, null).getExternalDeps();
  }

  private static class ExternalDepsResolutionResult {
    public static ExternalDepsResolutionResult
      EMPTY = new ExternalDepsResolutionResult(Collections.<ExternalDependency>emptySet(), Collections.<File>emptySet());
    private final Collection<ExternalDependency> externalDeps;
    private final Collection<File> resolvedFiles;

    private ExternalDepsResolutionResult(Collection<ExternalDependency> deps, Collection<File> files) {
      externalDeps = deps;
      resolvedFiles = files;
    }

    public Collection<File> getResolvedFiles() {
      return resolvedFiles;
    }

    public Collection<ExternalDependency> getExternalDeps() {
      return externalDeps;
    }
  }

  ExternalDepsResolutionResult resolveDependencies(@Nullable Configuration configuration, @Nullable String scope) {
    if (configuration == null) {
      return ExternalDepsResolutionResult.EMPTY;
    }

    if (configuration.getAllDependencies().isEmpty()) {
      return ExternalDepsResolutionResult.EMPTY;
    }

    final Collection<ExternalDependency> result = new LinkedHashSet<ExternalDependency>();

    final List<File> resolvedFileDependencies = new ArrayList<File>();

    if (!myIsPreview && isArtifactResolutionQuerySupported) {
      Class<? extends Component> jvmLibrary = null;
      try {
        jvmLibrary = (Class<? extends Component>)Class.forName("org.gradle.jvm.JvmLibrary");
      }
      catch (ClassNotFoundException ignored) {
      }
      if (jvmLibrary == null) {
        try {
          jvmLibrary = (Class<? extends Component>)Class.forName("org.gradle.runtime.jvm.JvmLibrary");
        }
        catch (ClassNotFoundException ignored) {
        }
      }

      if (jvmLibrary != null) {
        List<Class<? extends Artifact>> artifactTypes = new ArrayList<Class<? extends Artifact>>();

        if (myDownloadSources) { artifactTypes.add(SourcesArtifact.class); }
        if (myDownloadJavadoc) { artifactTypes.add(JavadocArtifact.class); }


        Set<ResolvedArtifact> resolvedArtifacts =
          configuration.getResolvedConfiguration().getLenientConfiguration().getArtifacts(Specs.SATISFIES_ALL);

        Multimap<ModuleVersionIdentifier, ResolvedArtifact> artifactMap = ArrayListMultimap.create();

        for (ResolvedArtifact artifact : resolvedArtifacts) {
          artifactMap.put(artifact.getModuleVersion().getId(), artifact);
        }


        boolean isBuildScriptConfiguration = myProject.getBuildscript().getConfigurations().contains(configuration);
        //noinspection GroovyAssignabilityCheck
        DependencyHandler dependencyHandler = isBuildScriptConfiguration ? myProject.getBuildscript().getDependencies() : myProject.getDependencies();

        List<ComponentIdentifier> components = new ArrayList<ComponentIdentifier>();
        for (ResolvedArtifact artifact : resolvedArtifacts) {
          if (!isProjectDependencyArtifact(artifact)) {
            components.add(toComponentIdentifier(artifact.getModuleVersion().getId()));
          }
        }

        Set<ComponentArtifactsResult> componentResults = dependencyHandler.createArtifactResolutionQuery()
          .forComponents(components)
          .withArtifacts(jvmLibrary, artifactTypes.toArray(new Class[artifactTypes.size()]))
          .execute()
          .getResolvedComponents();

        Map<ComponentIdentifier, ComponentArtifactsResult> componentResultsMap =
          new HashMap<ComponentIdentifier, ComponentArtifactsResult>();

        for (ComponentArtifactsResult artifactsResult : componentResults) {
          componentResultsMap.put(artifactsResult.getId(), artifactsResult);
        }

        Set<Configuration> processedConfigurations = new HashSet<Configuration>();
        Multimap<ModuleComponentIdentifier, ProjectDependency> configurationProjectDependencies =
          projectDeps(configuration, ArrayListMultimap.<ModuleComponentIdentifier, ProjectDependency>create(), processedConfigurations);

        ResolutionResult resolutionResult = configuration.getIncoming().getResolutionResult();
        if(!configuration.getResolvedConfiguration().hasError()) {
          Set<File> fileDeps = new LinkedHashSet<File>(configuration.getIncoming().getFiles().getFiles());

          for (ResolvedArtifact artifact : artifactMap.values()) {
            fileDeps.remove(artifact.getFile());
          }

          for (ProjectDependency dep : configurationProjectDependencies.values()) {
            final Set<File> intersection = new HashSet<File>(Sets.intersection(fileDeps, dep.resolve()));
            if (!intersection.isEmpty()) {
              DefaultFileCollectionDependency fileCollectionDependency = new DefaultFileCollectionDependency(intersection);
              fileCollectionDependency.setScope(scope);
              result.add(fileCollectionDependency);
              fileDeps.removeAll(intersection);
            }
          }

          for (File file : fileDeps) {
            DefaultFileCollectionDependency fileCollectionDependency = new DefaultFileCollectionDependency(Collections.singleton(file));
            fileCollectionDependency.setScope(scope);
            result.add(fileCollectionDependency);
          }
        }

        DependencyResultsTransformer dependencyResultsTransformer =
          new DependencyResultsTransformer(artifactMap, componentResultsMap, configurationProjectDependencies, scope);
        result.addAll(dependencyResultsTransformer.transform(resolutionResult.getRoot().getDependencies()));

        resolvedFileDependencies.addAll(dependencyResultsTransformer.resolvedDepsFiles);
      }
    }

    if (myIsPreview || !isArtifactResolutionQuerySupported) {
      Set<ExternalDependency> projectDependencies = findDependencies(configuration, configuration.getAllDependencies(), scope);
      result.addAll(projectDependencies);
    }
    Set<ExternalDependency> fileDependencies = findAllFileDependencies(configuration.getAllDependencies(), scope);
    // TODO investigate resolvedFileDependencies content
    fileDependencies.removeAll(resolvedFileDependencies);
    result.addAll(fileDependencies);

    return new ExternalDepsResolutionResult(new ArrayList<ExternalDependency>(result), resolvedFileDependencies);
  }

  Multimap<ModuleComponentIdentifier, ProjectDependency> projectDeps(Configuration conf,
                                                                     Multimap<ModuleComponentIdentifier, ProjectDependency> map,
                                                                     Set<Configuration> processedConfigurations) {
    if(!processedConfigurations.add(conf)) {
      return map;
    }
    for (Dependency dep : conf.getIncoming().getDependencies()) {
      if (dep instanceof ProjectDependency) {
        map.put(toComponentIdentifier(dep.getGroup(), dep.getName(), dep.getVersion()), (ProjectDependency)dep);
        projectDeps(getTargetConfiguration((ProjectDependency)dep), map, processedConfigurations);
      }
    }
    return map;
  }

  boolean containsAll(Configuration cfg, Collection<File> files) {
    for (File file : files) {
      if (!cfg.contains(file)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Collection<ExternalDependency> resolveDependencies(@NotNull SourceSet sourceSet) {
    Collection<ExternalDependency> result = new ArrayList<ExternalDependency>();

    // resolve compile dependencies
    boolean isMainSourceSet = sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME);
    String deprecatedCompileConfigurationName = isMainSourceSet ? "compile" : GUtil.toCamelCase(sourceSet.getName()) + "Compile";
    Configuration deprecatedCompileConfiguration = myProject.getConfigurations().findByName(deprecatedCompileConfigurationName);
    String compileConfigurationName = sourceSet.getCompileConfigurationName();
    Configuration compileClasspathConfiguration = myProject.getConfigurations().findByName(compileConfigurationName + "Classpath");
    Configuration originCompileConfiguration = myProject.getConfigurations().findByName(compileConfigurationName);
    Configuration compileConfiguration = compileClasspathConfiguration != null ? compileClasspathConfiguration : originCompileConfiguration;
    Configuration compileOnlyConfiguration =
      isJavaLibraryPluginSupported ? myProject.getConfigurations().findByName(sourceSet.getCompileOnlyConfigurationName()) : null;

    String compileScope = "COMPILE";
    ExternalDepsResolutionResult externalDepsResolutionResult = resolveDependencies(compileConfiguration, compileScope);
    Collection<ExternalDependency> compileDependencies = externalDepsResolutionResult.getExternalDeps();
    Collection<File> resolvedCompileFileDependencies = externalDepsResolutionResult.getResolvedFiles();

    // resolve runtime dependencies
    String runtimeConfigurationName = sourceSet.getRuntimeConfigurationName();
    Configuration runtimeClasspathConfiguration = myProject.getConfigurations().findByName(runtimeConfigurationName + "Classpath");
    Configuration originRuntimeConfiguration = myProject.getConfigurations().findByName(runtimeConfigurationName);
    Configuration runtimeConfiguration = runtimeClasspathConfiguration != null ? runtimeClasspathConfiguration : originRuntimeConfiguration;

    String runtimeScope = "RUNTIME";
    externalDepsResolutionResult = resolveDependencies(runtimeConfiguration, runtimeScope);
    Collection<ExternalDependency> runtimeDependencies = externalDepsResolutionResult.getExternalDeps();
    Collection<File> resolvedRuntimeFileDependencies = externalDepsResolutionResult.getResolvedFiles();


    String providedScope = "PROVIDED";
    Multimap<Object, ExternalDependency> resolvedMap = ArrayListMultimap.create();

    boolean checkCompileOnlyDeps = compileClasspathConfiguration != null && !originCompileConfiguration.getResolvedConfiguration().hasError();

    for (ExternalDependency dep : new DependencyTraverser(compileDependencies)) {
      final Collection<File> resolvedFiles = resolve(dep);
      resolvedMap.put(resolvedFiles, dep);

      // since version 3.4 compileOnly no longer extends compile
      // so, we can use compileOnly configuration for the check
      if (isJavaLibraryPluginSupported) {
        if (compileOnlyConfiguration != null && containsAll(compileOnlyConfiguration, resolvedFiles)) {
          // deprecated 'compile' configuration still can be used
          if (deprecatedCompileConfiguration == null || !containsAll(deprecatedCompileConfiguration, resolvedFiles)) {
            ((AbstractExternalDependency)dep).setScope(providedScope);
          }
        }
      }
      else {
        if (checkCompileOnlyDeps && ! containsAll(originCompileConfiguration, resolvedFiles) &&
            !containsAll(runtimeConfiguration, resolvedFiles)) {
          ((AbstractExternalDependency)dep).setScope(providedScope);
        }
      }
    }


    Multimap<Object, ExternalDependency> resolvedRuntimeMap = ArrayListMultimap.create();

    for (ExternalDependency dep : new DependencyTraverser(runtimeDependencies)) {
      Collection<ExternalDependency> dependencies = resolvedMap.get(resolve(dep));
      if (dependencies != null && !dependencies.isEmpty() && dep.getDependencies().isEmpty()) {
        runtimeDependencies.remove(dep);
        ((AbstractExternalDependency)dep).setScope(compileScope);
        for (ExternalDependency dependency : dependencies) {
          ((AbstractExternalDependency)dependency).setScope(compileScope);
        }
      }
      else {
        if (dependencies != null && !dependencies.isEmpty()) {
          ((AbstractExternalDependency)dep).setScope(compileScope);
        }
        resolvedRuntimeMap.put(resolve(dep), dep);
      }
    }

    resolvedMap.putAll(resolvedRuntimeMap);

    result.addAll(compileDependencies);
    result.addAll(runtimeDependencies);


    result = Lists.newArrayList(filter(result, not(isNull())));

    // merge file dependencies
    List<String> jvmLanguages = Lists.newArrayList("Java", "Groovy", "Scala");
    final String sourceSetCompileTaskPrefix = sourceSet.getName() == "main" ? "" : sourceSet.getName();

    List<String> compileTasks = Lists.transform(jvmLanguages, new Function<String, String>() {
      @Override
      public String apply(String s) {
        return "compile" + capitalize(sourceSetCompileTaskPrefix) + s;
      }
    });

    Map<File, Integer> compileClasspathOrder = new LinkedHashMap<File, Integer>();
    Set<File> compileClasspathFiles = new LinkedHashSet<File>();

    for (String task : compileTasks) {
      Task compileTask = myProject.getTasks().findByName(task);
      if (compileTask instanceof AbstractCompile) {
        try {
          List<File> files = new ArrayList<File>(((AbstractCompile)compileTask).getClasspath().getFiles());
          // TODO is this due to ordering?
          files.removeAll(compileClasspathFiles);
          compileClasspathFiles.addAll(files);
        } catch (Exception e) {
          // ignore
        }
      }
    }

    try {
      compileClasspathFiles = compileClasspathFiles.isEmpty() ? sourceSet.getCompileClasspath().getFiles() : compileClasspathFiles;
    } catch (Exception e) {
      // ignore
    }

    int order = 0;
    for (File file : compileClasspathFiles) {
      compileClasspathOrder.put(file, order++);
    }

    Map<File, Integer> runtimeClasspathOrder = new LinkedHashMap<File, Integer>();
    order = 0;
    Set<File> runtimeClasspathFiles = new LinkedHashSet<File>();
    try {
      Set<File> files = sourceSet.getRuntimeClasspath().getFiles();
      for (File file : files) {
        runtimeClasspathOrder.put(file, order++);
      }
      runtimeClasspathFiles.addAll(files);
    } catch (Exception e) {
      // ignore
    }

    runtimeClasspathFiles.removeAll(compileClasspathFiles);
    runtimeClasspathFiles.removeAll(sourceSet.getOutput().getFiles());
    compileClasspathFiles.removeAll(sourceSet.getOutput().getFiles());

    Multimap<String, File> resolvedDependenciesMap = ArrayListMultimap.create();
    resolvedDependenciesMap.putAll(compileScope, resolvedCompileFileDependencies);
    resolvedDependenciesMap.putAll(runtimeScope, resolvedRuntimeFileDependencies);
    Project rootProject = myProject.getRootProject();

    for (ExternalDependency dependency : new DependencyTraverser(result)) {
      String scope = dependency.getScope();
      order = -1;
      if (dependency instanceof ExternalProjectDependency) {
        ExternalProjectDependency projectDependency = (ExternalProjectDependency)dependency;

        Project project = rootProject.findProject(projectDependency.getProjectPath());
        if (project != null) {
          Configuration configuration = project.getConfigurations().findByName(projectDependency.getConfigurationName());

          if (configuration != null) {
            for (File file : configuration.getAllArtifacts().getFiles().getFiles()) {
              resolvedDependenciesMap.put(scope, file);
              Map<File, Integer> classpathOrderMap = scope == compileScope ? compileClasspathOrder :
                                                     scope == runtimeScope ? runtimeClasspathOrder : null;
              if (classpathOrderMap != null) {
                Integer fileOrder = classpathOrderMap.get(file);
                if (fileOrder != null && (order == -1 || fileOrder < order)) {
                  order = fileOrder;
                }
              }
            }
          }

          //noinspection GrUnresolvedAccess
          if (project.hasProperty("sourceSets")
              && (project.property("sourceSets") instanceof SourceSetContainer)
              && ((SourceSetContainer)project.property("sourceSets")).findByName("main") != null) {
            //noinspection GrUnresolvedAccess
            addSourceSetOutputDirsAsSingleEntryLibraries(result,
                                                         ((SourceSetContainer)project.property("sourceSets")).findByName("main"),
                                                         runtimeClasspathOrder, scope);
          }
        }
      } else if (dependency instanceof ExternalLibraryDependency) {
        final File file = ((ExternalLibraryDependency)dependency).getFile();
        resolvedDependenciesMap.put(scope, file);
        Map<File, Integer> classpathOrderMap = scope == compileScope ? compileClasspathOrder :
                                               scope == runtimeScope ? runtimeClasspathOrder : null;
        if (classpathOrderMap != null) {
          Integer fileOrder = classpathOrderMap.get(file);
          order = fileOrder != null ? fileOrder : -1;
        }
      } else if (dependency instanceof FileCollectionDependency) {
        for (File file : ((FileCollectionDependency)dependency).getFiles()) {
          resolvedDependenciesMap.put(scope, file);
          Map<File, Integer> classpathOrderMap = scope == compileScope ? compileClasspathOrder :
                                                 scope == runtimeScope ? runtimeClasspathOrder : null;
          if (classpathOrderMap != null) {
            Integer fileOrder = classpathOrderMap.get(file);
            if (fileOrder != null && (order == -1 || fileOrder < order)) {
              order = fileOrder;
            }
            if (order == 0) break;
          }
        }
      }

      if (dependency instanceof AbstractExternalDependency) {
        ((AbstractExternalDependency)dependency).setClasspathOrder(order);
      }
    }

    compileClasspathFiles.removeAll(resolvedDependenciesMap.get(compileScope));
    compileClasspathFiles.removeAll(resolvedDependenciesMap.get(providedScope));
    runtimeClasspathFiles.removeAll(resolvedDependenciesMap.get(runtimeScope));
    runtimeClasspathFiles.removeAll(resolvedDependenciesMap.get(compileScope));
    runtimeClasspathFiles.removeAll(resolvedDependenciesMap.get(providedScope));

    Collection<ExternalDependency> fileDependencies = new ArrayList<ExternalDependency>();
    mapFileDependencies(runtimeClasspathFiles, runtimeScope, fileDependencies);
    mapFileDependencies(compileClasspathFiles, compileScope, fileDependencies);

    for (ExternalDependency dependency : fileDependencies) {
      String scope = dependency.getScope();
      order = -1;
      if (dependency instanceof ExternalLibraryDependency) {
        Map<File, Integer> classpathOrderMap = scope == compileScope ? compileClasspathOrder :
                                               scope == runtimeScope ? runtimeClasspathOrder : null;
        if (classpathOrderMap != null) {
          Integer fileOrder = classpathOrderMap.get(((ExternalLibraryDependency)dependency).getFile());
          order = fileOrder != null ? fileOrder : -1;
        }
      }
      if (dependency instanceof AbstractExternalDependency) {
        ((AbstractExternalDependency)dependency).setClasspathOrder(order);
      }
    }

    result.addAll(fileDependencies);

    if (!compileClasspathFiles.isEmpty()) {
      final DefaultFileCollectionDependency compileClasspathFilesDependency = new DefaultFileCollectionDependency(compileClasspathFiles);
      compileClasspathFilesDependency.setScope(compileScope);

      order = -1;

      for (File file : compileClasspathFiles) {
        Integer fileOrder = compileClasspathOrder.get(file);
        if (fileOrder != null && (order == -1 || fileOrder < order)) {
          order = fileOrder;
        }
        if (order == 0) break;
      }

      if (order != -1) {
        compileClasspathFilesDependency.setClasspathOrder(order);
      }

      result.add(compileClasspathFilesDependency);
      for (File file : compileClasspathFiles) {
        SourceSet outputDirSourceSet = mySourceSetFinder.findByArtifact(file.getPath());
        if(outputDirSourceSet != null) {
          addSourceSetOutputDirsAsSingleEntryLibraries(result, outputDirSourceSet, compileClasspathOrder, compileScope);
        }
      }
    }

    if (!runtimeClasspathFiles.isEmpty()) {
      final DefaultFileCollectionDependency runtimeClasspathFilesDependency = new DefaultFileCollectionDependency(runtimeClasspathFiles);
      runtimeClasspathFilesDependency.setScope(runtimeScope);

      order = -1;
      for (File file : runtimeClasspathFiles) {
        Integer fileOrder = runtimeClasspathOrder.get(file);
        if (fileOrder != null && (order == -1 || fileOrder < order)) {
          order = fileOrder;
        }
        if (order == 0) break;
      }

      runtimeClasspathFilesDependency.setClasspathOrder(order);
      result.add(runtimeClasspathFilesDependency);

      for (File file : runtimeClasspathFiles) {
        SourceSet outputDirSourceSet = mySourceSetFinder.findByArtifact(file.getPath());
        if (outputDirSourceSet != null) {
          addSourceSetOutputDirsAsSingleEntryLibraries(result, outputDirSourceSet, runtimeClasspathOrder, runtimeScope);
        }
      }
    }

    addSourceSetOutputDirsAsSingleEntryLibraries(result, sourceSet, runtimeClasspathOrder, runtimeScope);

    // handle provided dependencies
    final Set<Configuration> providedConfigurations = new LinkedHashSet<Configuration>();
    resolvedMap = ArrayListMultimap.create();
    for (ExternalDependency dep : new DependencyTraverser(result)) {
      resolvedMap.put(resolve(dep), dep);
    }

    if (sourceSet.getName().equals("main") && myProject.getPlugins().findPlugin(WarPlugin.class) != null) {
      providedConfigurations.add(myProject.getConfigurations().findByName("providedCompile"));
      providedConfigurations.add(myProject.getConfigurations().findByName("providedRuntime"));
    }

    final IdeaPlugin ideaPlugin = myProject.getPlugins().findPlugin(IdeaPlugin.class);

    if (ideaPlugin != null) {
      Map<String, Map<String, Collection<Configuration>>> scopes = ideaPlugin.getModel().getModule().getScopes();
      Map<String, Collection<Configuration>> providedPlusScopes = scopes.get(providedScope);

      if (providedPlusScopes != null && providedPlusScopes.get("plus") != null) {
        Iterable<Configuration> ideaPluginProvidedConfigurations = filter(providedPlusScopes.get("plus"), new Predicate<Configuration>() {
          @Override

          public boolean apply(Configuration cfg) {
            // filter default 'compileClasspath' for slight optimization since it has been already processed as compile dependencies
            return !cfg.getName().equals("compileClasspath")
                   // since gradle 3.4 'idea' plugin PROVIDED scope.plus contains 'providedCompile' and 'providedRuntime' configurations
                   // see https://github.com/gradle/gradle/commit/c46897ae840c5ebb32946009c83d861ee194ab96#diff-0fa13ec419e839ef2d355b7feb88b815R432
                   && !providedConfigurations.contains(cfg);
          }
        });

        for (Configuration configuration : ideaPluginProvidedConfigurations) {
          Collection<ExternalDependency> providedDependencies = resolveDependencies(configuration, providedScope).getExternalDeps();
          for(ExternalDependency it : new DependencyTraverser(providedDependencies)) {
            resolvedMap.put(resolve(it), it);
          }
          result.addAll(providedDependencies);
        }
      }
    }

    for (Configuration cfg : providedConfigurations) {
      Collection<ExternalDependency> providedDependencies = resolveDependencies(cfg, providedScope).getExternalDeps();
      for (ExternalDependency dep : new DependencyTraverser(providedDependencies)) {
        Collection<ExternalDependency> dependencies = resolvedMap.get(resolve(dep));
        if (!dependencies.isEmpty()) {
          if (dep.getDependencies().isEmpty()) {
            providedDependencies.remove(dep);
          }
          for (ExternalDependency depForScope: dependencies) {
            ((AbstractExternalDependency)depForScope).setScope(providedScope);
          }
        } else {
          resolvedMap.put(resolve(dep), dep);
        }
      }
      result.addAll(providedDependencies);
    }

    return removeDuplicates(resolvedMap, result);
  }

  private static List<ExternalDependency> removeDuplicates(Multimap<Object, ExternalDependency> resolvedMap, Collection<ExternalDependency> result) {

    for (Collection<ExternalDependency> val : resolvedMap.asMap().values()) {
      List<ExternalDependency> toRemove = new ArrayList<ExternalDependency>();
      boolean isCompileScope = false;
      boolean isProvidedScope = false;

      for (ExternalDependency dep : val) {
        if (dep.getDependencies().isEmpty()) {
          toRemove.add(dep);
          if (dep.getScope().equals("COMPILE")) {
            isCompileScope = true;
          } else if (dep.getScope().equals("PROVIDED")) {
            isProvidedScope = true;
          }
        }
      }

      if (toRemove.size() != val.size()) {
        result.removeAll(toRemove);
      } else if (toRemove.size() > 1) {
        toRemove = toRemove.subList(1, toRemove.size());
        result.removeAll(toRemove);
      }

      if (!toRemove.isEmpty()) {
        List<ExternalDependency> retained = new ArrayList<ExternalDependency>(val);
        retained.removeAll(toRemove);
        if(!retained.isEmpty()) {
          ExternalDependency retainedDependency = retained.iterator().next();
          if(retainedDependency instanceof AbstractExternalDependency && !retainedDependency.getScope().equals("COMPILE")) {
            if (isCompileScope) {
              ((AbstractExternalDependency)retainedDependency).setScope("COMPILE");
            } else if (isProvidedScope) {
              ((AbstractExternalDependency)retainedDependency).setScope("PROVIDED");
            }
          }
        }
      }
    }


    return Lists.newArrayList(filter(result, not(isNull())));
  }

  @NotNull
  static Collection<File> resolve(ExternalDependency dependency) {
    if (dependency instanceof ExternalLibraryDependency) {
      return Collections.singleton(((ExternalLibraryDependency)dependency).getFile());
    } else if (dependency instanceof FileCollectionDependency) {
      return ((FileCollectionDependency)dependency).getFiles();
    } else if (dependency instanceof ExternalMultiLibraryDependency) {
      return ((ExternalMultiLibraryDependency)dependency).getFiles();
    } else if (dependency instanceof ExternalProjectDependency) {
      return ((ExternalProjectDependency)dependency).getProjectDependencyArtifacts();
    }
    return Collections.emptySet();
  }

  private static void addSourceSetOutputDirsAsSingleEntryLibraries(
    Collection<ExternalDependency> dependencies,
    SourceSet sourceSet,
    Map<File, Integer> classpathOrder,
    String scope) {
    Set<File> runtimeOutputDirs = sourceSet.getOutput().getDirs().getFiles();
    for (File dir : runtimeOutputDirs) {
      DefaultFileCollectionDependency runtimeOutputDirsDependency = new DefaultFileCollectionDependency(Collections.singleton(dir));
      runtimeOutputDirsDependency.setScope(scope);
      Integer fileOrder = classpathOrder.get(dir);
      runtimeOutputDirsDependency.setClasspathOrder(fileOrder != null ? fileOrder : -1);
      dependencies.add(runtimeOutputDirsDependency);
    }
  }


  @Nullable
  ExternalLibraryDependency resolveLibraryByPath(File file, String scope) {
    File modules2Dir = new File(myProject.getGradle().getGradleUserHomeDir(), "caches/modules-2/files-2.1");
    return resolveLibraryByPath(file, modules2Dir, scope);
  }

  @Nullable
  static ExternalLibraryDependency resolveLibraryByPath(File file, File modules2Dir, String scope) {
    File sourcesFile = null;
    try {
      String modules2Path = modules2Dir.getCanonicalPath();
      String filePath = file.getCanonicalPath();

      if (filePath.startsWith(modules2Path)) {
        List<File> parents = new ArrayList<File>();
        File parent = file.getParentFile();
        while(parent != null && !parent.getName().equals(modules2Dir.getName())) {
          parents.add(parent);
          parent = parent.getParentFile();
        }

        File groupDir = parents.get(parents.size() - 1);
        File artifactDir = parents.get(parents.size() - 2);
        File versionDir = parents.get(parents.size() - 3);

        File parentFile = versionDir;
        if (parentFile != null) {
          File[] hashDirs = parentFile.listFiles();
          if (hashDirs != null) {
            for (File hashDir : hashDirs) {
              File[] sourcesJars = hashDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                  return name.endsWith("sources.jar");
                }
              });

              if (sourcesJars != null && sourcesJars.length > 0) {
                sourcesFile = sourcesJars[0];
                break;
              }
            }

            String packaging = resolvePackagingType(file);
            String classifier = resolveClassifier(artifactDir.getName(), versionDir.getName(), file);

            DefaultExternalLibraryDependency defaultDependency = new DefaultExternalLibraryDependency();

            defaultDependency.setName(artifactDir.getName());
            defaultDependency.setGroup(groupDir.getName());
            defaultDependency.setPackaging(packaging);
            defaultDependency.setClassifier(classifier);
            defaultDependency.setVersion(versionDir.getName());
            defaultDependency.setFile(file);
            defaultDependency.setSource(sourcesFile);
            defaultDependency.setScope(scope);

            return defaultDependency;
          }
        }
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    return null;
  }

  void mapFileDependencies(Set<File> fileDependencies, String scope, Collection<ExternalDependency> dependencies) {
    File modules2Dir = new File(myProject.getGradle().getGradleUserHomeDir(), "caches/modules-2/files-2.1");
    List<File> toRemove = new ArrayList<File>();
    for (File file : fileDependencies) {
      ExternalLibraryDependency libraryDependency = resolveLibraryByPath(file, modules2Dir, scope);
      if (libraryDependency != null) {
        dependencies.add(libraryDependency);
        toRemove.add(file);
      } else {
        //noinspection GrUnresolvedAccess
        String name = getNameWithoutExtension(file);
        File sourcesFile = new File(file.getParentFile(), name + "-sources.jar");
        if (sourcesFile.exists()) {
          libraryDependency = new DefaultExternalLibraryDependency();
          DefaultExternalLibraryDependency defLD = (DefaultExternalLibraryDependency)libraryDependency;
          defLD.setFile(file);
          defLD.setSource(sourcesFile);
          defLD.setScope(scope);

          dependencies.add(libraryDependency);
          toRemove.add(file);
        }
      }
    }

    fileDependencies.removeAll(toRemove);
  }

  @NotNull
  static String resolvePackagingType(File file) {
    if (file == null) return "jar";
    String path = file.getPath();
    int index = path.lastIndexOf('.');
    if (index < 0) return "jar";
    return path.substring(index + 1);
  }

  @Nullable
  static String resolveClassifier(String name, String version, File file) {
    String libraryFileName = getNameWithoutExtension(file);
    final String mavenLibraryFileName = name + "-" + version;
    if (!mavenLibraryFileName.equals(libraryFileName)) {
      Matcher matcher = Pattern.compile(name + "-" + version + "-(.*)").matcher(libraryFileName);
      if (matcher.matches()) {
        return matcher.group(1);
      }
    }
    return null;
  }

  static String getNameWithoutExtension(File file) {
    if (file == null) return null;
    String name = file.getName();
    int i = name.lastIndexOf('.');
    if (i != -1) {
      name = name.substring(0, i);
    }
    return name;
  }

  public ModuleComponentIdentifier toComponentIdentifier(ModuleVersionIdentifier id) {
    return new ModuleComponentIdentifierImpl(id.getGroup(), id.getName(), id.getVersion());
  }

  public ModuleComponentIdentifier toComponentIdentifier(@NotNull String group, @NotNull String module, @NotNull String version) {
    return new ModuleComponentIdentifierImpl(group, module, version);
  }

  private static Set<ExternalDependency> findAllFileDependencies(Collection<Dependency> dependencies, String scope) {
    Set<ExternalDependency> result = new LinkedHashSet<ExternalDependency>();

    for (Dependency dep : dependencies) {
      try {
        if (dep instanceof SelfResolvingDependency && !(dep instanceof ProjectDependency)) {
          Set<File> files = ((SelfResolvingDependency)dep).resolve();
          if (files != null && !files.isEmpty()) {
            AbstractExternalDependency dependency = new DefaultFileCollectionDependency(files);
            dependency.setScope(scope);
            result.add(dependency);
          }
        }
      }
      catch (Exception e) {
        // ignore
      }
    }

    return result;
  }

  private Set<ExternalDependency> findDependencies(
    Configuration configuration,
    Collection<Dependency> dependencies,
    String scope) {
    Set<ExternalDependency> result = new LinkedHashSet<ExternalDependency>();

    Set<ResolvedArtifact> resolvedArtifacts = myIsPreview ? new HashSet<ResolvedArtifact>() :
                                              configuration.getResolvedConfiguration().getLenientConfiguration()
                                                .getArtifacts(Specs.SATISFIES_ALL);

    Multimap<MyModuleIdentifier, ResolvedArtifact> artifactMap = ArrayListMultimap.create();
    for (ResolvedArtifact artifact : resolvedArtifacts) {
      artifactMap.put(toMyModuleIdentifier(artifact.getModuleVersion().getId()), artifact);
    }

    for (Dependency it : dependencies) {
      try {
        if (it instanceof ProjectDependency) {
          Project project = ((ProjectDependency)it).getDependencyProject();
          Configuration targetConfiguration = getTargetConfiguration((ProjectDependency)it);

          DefaultExternalProjectDependency projectDependency = new DefaultExternalProjectDependency();
          projectDependency.setName(project.getName());
          projectDependency.setGroup(project.getGroup().toString());
          projectDependency.setVersion(project.getVersion().toString());
          projectDependency.setScope(scope);
          projectDependency.setProjectPath(project.getPath());
          projectDependency.setConfigurationName(targetConfiguration.getName());
          projectDependency.setProjectDependencyArtifacts(targetConfiguration.getAllArtifacts().getFiles().getFiles());

          result.add(projectDependency);
        } else if (it != null) {
          Collection<ResolvedArtifact> artifactsResult = artifactMap.get(toMyModuleIdentifier(it.getName(), it.getGroup()));
          if (artifactsResult != null && !artifactsResult.isEmpty()) {
            ResolvedArtifact artifact = artifactsResult.iterator().next();
            String packaging = artifact.getExtension() != null ? artifact.getExtension() : "jar";
            String classifier = artifact.getClassifier();
            final ExternalLibraryDependency resolvedDep = resolveLibraryByPath(artifact.getFile(), scope);
            File sourcesFile = resolvedDep == null ? null : resolvedDep.getSource();

            DefaultExternalLibraryDependency libraryDependency = new DefaultExternalLibraryDependency();
            libraryDependency.setName(it.getName());
            libraryDependency.setGroup(it.getGroup());
            libraryDependency.setPackaging(packaging);
            libraryDependency.setClassifier(classifier);
            libraryDependency.setVersion(artifact.getModuleVersion().getId().getVersion());
            libraryDependency.setScope(scope);
            libraryDependency.setFile(artifact.getFile());
            libraryDependency.setSource(sourcesFile);

            result.add(libraryDependency);
          } else {
            if (!(it instanceof SelfResolvingDependency) && !myIsPreview) {
              final DefaultUnresolvedExternalDependency dependency = new DefaultUnresolvedExternalDependency();
              dependency.setName(it.getName());
              dependency.setGroup(it.getGroup());
              dependency.setVersion(it.getVersion());
              dependency.setScope(scope);
              dependency.setFailureMessage("Could not find " + it.getGroup() + ":" + it.getName() + ":" + it.getVersion());
              result.add(dependency);
            }
          }
        }
      }
      catch (Exception e) {
        // ignore
      }
    }

    return result;
  }

  private static Configuration getTargetConfiguration(ProjectDependency projectDependency) {

    try {
      return !is4OrBetter ?  (Configuration)projectDependency.getClass().getMethod("getProjectConfiguration").invoke(projectDependency) :
             projectDependency.getDependencyProject().getConfigurations().getByName(projectDependency.getTargetConfiguration() != null
                                                                                    ? projectDependency.getTargetConfiguration()
                                                                                    : "default");
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }


  class DependencyResultsTransformer {
    Collection<DependencyResult> handledDependencyResults;
    Multimap<ModuleVersionIdentifier, ResolvedArtifact> artifactMap;
    Map<ComponentIdentifier, ComponentArtifactsResult> componentResultsMap;
    Multimap<ModuleComponentIdentifier, ProjectDependency> configurationProjectDependencies;
    String scope;
    Set<File> resolvedDepsFiles = new HashSet<File>();

    DependencyResultsTransformer(
      Multimap<ModuleVersionIdentifier, ResolvedArtifact> artifactMap,
      Map<ComponentIdentifier, ComponentArtifactsResult> componentResultsMap,
      Multimap<ModuleComponentIdentifier, ProjectDependency> configurationProjectDependencies,
      String scope) {
      this.handledDependencyResults = Lists.newArrayList();
      this.artifactMap = artifactMap;
      this.componentResultsMap = componentResultsMap;
      this.configurationProjectDependencies = configurationProjectDependencies;
      this.scope = scope;
    }

    Set<ExternalDependency> transform(Collection<? extends DependencyResult> dependencyResults) {

      Set<ExternalDependency> dependencies = new LinkedHashSet<ExternalDependency>();
      for (DependencyResult dependencyResult : dependencyResults) {

        // dependency cycles check
        if (!handledDependencyResults.contains(dependencyResult)) {
          handledDependencyResults.add(dependencyResult);

          if (dependencyResult instanceof ResolvedDependencyResult) {
            ResolvedComponentResult componentResult = ((ResolvedDependencyResult)dependencyResult).getSelected();
            ComponentSelector componentSelector = dependencyResult.getRequested();
            ModuleComponentIdentifier componentIdentifier = toComponentIdentifier(componentResult.getModuleVersion());

            String name = componentResult.getModuleVersion().getName();
            String group = componentResult.getModuleVersion().getGroup();
            String version = componentResult.getModuleVersion().getVersion();
            String selectionReason = componentResult.getSelectionReason().getDescription();

            boolean resolveFromArtifacts = componentSelector instanceof ModuleComponentSelector;

            if (componentSelector instanceof ProjectComponentSelector) {
              Collection<ProjectDependency> projectDependencies = configurationProjectDependencies.get(componentIdentifier);
              Collection<Configuration> dependencyConfigurations;
              if (projectDependencies.isEmpty()) {
                Project dependencyProject = myProject.findProject(((ProjectComponentSelector)componentSelector).getProjectPath());
                if (dependencyProject != null) {
                  Configuration dependencyProjectConfiguration =
                    dependencyProject.getConfigurations().getByName(Dependency.DEFAULT_CONFIGURATION);
                  dependencyConfigurations = Collections.singleton(dependencyProjectConfiguration);
                } else {
                  dependencyConfigurations = Collections.emptySet();
                  resolveFromArtifacts = true;
                  selectionReason = "composite build substitution";
                }
              } else {
                dependencyConfigurations = new ArrayList<Configuration>();
                for (ProjectDependency dependency : projectDependencies) {
                  dependencyConfigurations.add(getTargetConfiguration(dependency));
                }
              }

              for (Configuration it : dependencyConfigurations) {
                if (it.getName().equals(Dependency.DEFAULT_CONFIGURATION)) {
                  DefaultExternalProjectDependency dependency = new DefaultExternalProjectDependency();
                  dependency.setName( name);
                  dependency.setGroup(group);
                  dependency.setVersion(version);
                  dependency.setScope(scope);
                  dependency.setSelectionReason(selectionReason);
                  dependency.setProjectPath(((ProjectComponentSelector)componentSelector).getProjectPath());
                  dependency.setConfigurationName(it.getName());
                  dependency.setProjectDependencyArtifacts(it.getAllArtifacts().getFiles().getFiles());

                  resolvedDepsFiles.addAll(dependency.getProjectDependencyArtifacts());

                  if (it.getArtifacts().size() == 1) {
                    PublishArtifact publishArtifact = it.getAllArtifacts().iterator().next();
                    dependency.setClassifier(publishArtifact.getClassifier());
                    dependency.setPackaging(publishArtifact.getExtension() != null ? publishArtifact.getExtension() : "jar");
                  }

                  if (!componentResult.equals(dependencyResult.getFrom())) {
                    dependency.getDependencies().addAll(
                      transform(componentResult.getDependencies())
                    );
                  }
                  dependencies.add(dependency);
                }
                else {
                  DefaultExternalProjectDependency dependency = new DefaultExternalProjectDependency();
                  dependency.setName(name);
                  dependency.setGroup(group);
                  dependency.setVersion(version);
                  dependency.setScope(scope);
                  dependency.setSelectionReason(selectionReason);
                  dependency.setProjectPath(((ProjectComponentSelector)componentSelector).getProjectPath());
                  dependency.setConfigurationName(it.getName());
                  dependency.setProjectDependencyArtifacts(it.getAllArtifacts().getFiles().getFiles());

                  resolvedDepsFiles.addAll(dependency.getProjectDependencyArtifacts());

                  if (it.getArtifacts().size() == 1) {
                    PublishArtifact publishArtifact = it.getAllArtifacts().iterator().next();
                    dependency.setClassifier(publishArtifact.getClassifier());
                    dependency.setPackaging(publishArtifact.getExtension() != null ? publishArtifact.getExtension() : "jar");
                  }

                  if (!componentResult.equals(dependencyResult.getFrom())) {

                    dependency.getDependencies().addAll(
                      transform(componentResult.getDependencies())
                    );
                  }

                  dependencies.add(dependency);

                  List<File> files = new ArrayList<File>();
                  PublishArtifactSet artifacts = it.getArtifacts();
                  if (artifacts != null && !artifacts.isEmpty()) {
                    PublishArtifact artifact = artifacts.iterator().next();
                    final MetaProperty taskProperty = DefaultGroovyMethods.hasProperty(artifact, "archiveTask");
                    if (taskProperty != null && (taskProperty.getProperty(artifact) instanceof AbstractArchiveTask)) {

                      AbstractArchiveTask archiveTask = (AbstractArchiveTask)taskProperty.getProperty(artifact);
                      resolvedDepsFiles.add(new File(archiveTask.getDestinationDir(), archiveTask.getArchiveName()));


                      try {
                        final Method mainSpecGetter = AbstractCopyTask.class.getDeclaredMethod("getMainSpec");
                        mainSpecGetter.setAccessible(true);
                        Object mainSpec = mainSpecGetter.invoke(archiveTask);

                        final List<MetaMethod> sourcePathGetters =
                          DefaultGroovyMethods.respondsTo(mainSpec, "getSourcePaths", new Object[]{});
                        if (!sourcePathGetters.isEmpty()) {
                          Set<Object> sourcePaths = (Set<Object>)sourcePathGetters.get(0).doMethodInvoke(mainSpec, new Object[]{});
                          if (sourcePaths != null) {
                            for (Object path : sourcePaths) {
                              if (path instanceof String) {
                                File file = new File((String)path);
                                if (file.isAbsolute()) {
                                  files.add(file);
                                }
                              }
                              else if (path instanceof SourceSetOutput) {
                                files.addAll(((SourceSetOutput)path).getFiles());
                              }
                            }
                          }
                        }
                      } catch (Exception e) {
                        throw new RuntimeException(e);
                      }
                    }
                  }

                  if (!files.isEmpty()) {
                    final DefaultFileCollectionDependency fileCollectionDependency = new DefaultFileCollectionDependency(files);
                    fileCollectionDependency.setScope(scope);
                    dependencies.add(fileCollectionDependency);
                    resolvedDepsFiles.addAll(files);
                  }
                }
              }
            }

            if (resolveFromArtifacts) {
              Collection<ResolvedArtifact> artifacts = artifactMap.get(componentResult.getModuleVersion());

              if (artifacts != null && artifacts.isEmpty()) {
                dependencies.addAll(
                  transform(componentResult.getDependencies())
                );
              }

              boolean first = true;

              if (artifacts != null) {
                for (ResolvedArtifact artifact: artifacts) {
                  String packaging = artifact.getExtension() != null ? artifact.getExtension() : "jar";
                  String classifier = artifact.getClassifier();
                  final ExternalDependency dependency;
                  if (isProjectDependencyArtifact(artifact)) {
                    ProjectComponentIdentifier artifactComponentIdentifier =
                      (ProjectComponentIdentifier)artifact.getId().getComponentIdentifier();

                    dependency = new DefaultExternalProjectDependency();
                    DefaultExternalProjectDependency dDep = (DefaultExternalProjectDependency)dependency;
                    dDep.setName(name);
                    dDep.setGroup(group);
                    dDep.setVersion(version);
                    dDep.setScope(scope);
                    dDep.setSelectionReason(selectionReason);
                    dDep.setProjectPath(artifactComponentIdentifier.getProjectPath());
                    dDep.setConfigurationName(Dependency.DEFAULT_CONFIGURATION);

                    List<File> files = new ArrayList<File>();
                    for (ResolvedArtifact resolvedArtifact : artifactMap.get(componentResult.getModuleVersion())) {
                      files.add(resolvedArtifact.getFile());
                    }
                    dDep.setProjectDependencyArtifacts(files);
                    resolvedDepsFiles.addAll(dDep.getProjectDependencyArtifacts());
                  }

                  else {
                    dependency = new DefaultExternalLibraryDependency();
                    DefaultExternalLibraryDependency dDep = (DefaultExternalLibraryDependency)dependency;
                    dDep.setName(name);
                    dDep.setGroup(group);
                    dDep.setPackaging(packaging);
                    dDep.setClassifier(classifier);
                    dDep.setVersion(version);
                    dDep.setScope(scope);
                    dDep.setSelectionReason(selectionReason);
                    dDep.setFile(artifact.getFile());

                    ComponentArtifactsResult artifactsResult = componentResultsMap.get(componentIdentifier);
                    if (artifactsResult != null) {
                      ResolvedArtifactResult sourcesResult = findMatchingArtifact(artifact, artifactsResult, SourcesArtifact.class);
                      if (sourcesResult != null) {
                        ((DefaultExternalLibraryDependency)dependency).setSource(sourcesResult.getFile());
                      }

                      ResolvedArtifactResult javadocResult = findMatchingArtifact(artifact, artifactsResult, JavadocArtifact.class);
                      if (javadocResult != null) {
                        ((DefaultExternalLibraryDependency)dependency).setJavadoc(javadocResult.getFile());
                      }
                    }
                  }

                  if (first) {
                    dependency.getDependencies().addAll(
                      transform(componentResult.getDependencies())
                    );
                    first = false;
                  }

                  dependencies.add(dependency);
                  resolvedDepsFiles.add(artifact.getFile());
                }
              }
            }
          }

          if (dependencyResult instanceof UnresolvedDependencyResult) {
            ComponentSelector attempted = ((UnresolvedDependencyResult)dependencyResult).getAttempted();
            if (attempted instanceof ModuleComponentSelector) {
              final ModuleComponentSelector attemptedMCSelector = (ModuleComponentSelector)attempted;
              final DefaultUnresolvedExternalDependency dependency = new DefaultUnresolvedExternalDependency();
              dependency.setName(attemptedMCSelector.getModule());
              dependency.setGroup(attemptedMCSelector.getGroup());
              dependency.setVersion(attemptedMCSelector.getVersion());
              dependency.setScope(scope);
              dependency.setFailureMessage(((UnresolvedDependencyResult)dependencyResult).getFailure().getMessage());

              dependencies.add(dependency);
            }
          }
        }
      }

      return dependencies;
    }
  }

  @Nullable
  private static ResolvedArtifactResult findMatchingArtifact(ResolvedArtifact artifact,
                                                             ComponentArtifactsResult componentArtifacts,
                                                             Class<? extends Artifact> artifactType) {
    String baseName = Files.getNameWithoutExtension(artifact.getFile().getName());
    Set<ArtifactResult> artifactResults = componentArtifacts.getArtifacts(artifactType);

    if (artifactResults.size() == 1) {
      ArtifactResult artifactResult = artifactResults.iterator().next();
      return artifactResult instanceof ResolvedArtifactResult ? (ResolvedArtifactResult)artifactResult : null;
    }

    for (ArtifactResult result : artifactResults) {
      if (result instanceof ResolvedArtifactResult && ((ResolvedArtifactResult)result).getFile().getName().startsWith(baseName)) {
        return (ResolvedArtifactResult)result;
      }
    }
    return null;
  }

  private static boolean isProjectDependencyArtifact(ResolvedArtifact artifact) {
    return isDependencySubstitutionsSupported && artifact.getId().getComponentIdentifier() instanceof ProjectComponentIdentifier;
  }

  private static MyModuleIdentifier toMyModuleIdentifier(ModuleVersionIdentifier id) {
    return new MyModuleIdentifier(id.getName(), id.getGroup());
  }

  private static MyModuleIdentifier toMyModuleIdentifier(String name, String group) {
    return new MyModuleIdentifier(name, group);
  }

  static class MyModuleIdentifier {
    String name;
    String group;

    public MyModuleIdentifier(String name, String group) {
      this.name = name;
      this.group = group;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MyModuleIdentifier that = (MyModuleIdentifier)o;

      if (name != null ? !name.equals(that.name) : that.name != null) return false;
      if (group != null ? !group.equals(that.group) : that.group != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = (group != null ? group.hashCode() : 0);
      result = 31 * result + (name != null ? name.hashCode() : 0);
      return result;
    }

    @Override
    public String toString() {
      return group + ":" + name;
    }
  }
}
