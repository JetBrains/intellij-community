// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.


package org.jetbrains.plugins.gradle.tooling.util.resolve;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.internal.impldep.com.google.common.base.Function;
import org.gradle.internal.impldep.com.google.common.base.Predicate;
import org.gradle.internal.impldep.com.google.common.collect.ArrayListMultimap;
import org.gradle.internal.impldep.com.google.common.collect.Lists;
import org.gradle.internal.impldep.com.google.common.collect.Multimap;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.model.ExternalDependency;
import org.jetbrains.plugins.gradle.model.FileCollectionDependency;
import org.jetbrains.plugins.gradle.tooling.util.DependencyResolver;
import org.jetbrains.plugins.gradle.tooling.util.DependencyTraverser;
import org.jetbrains.plugins.gradle.tooling.util.ModuleComponentIdentifierImpl;
import org.jetbrains.plugins.gradle.tooling.util.SourceSetCachedFinder;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.codehaus.groovy.runtime.StringGroovyMethods.capitalize;
import static org.gradle.internal.impldep.com.google.common.base.Predicates.isNull;
import static org.gradle.internal.impldep.com.google.common.base.Predicates.not;
import static org.gradle.internal.impldep.com.google.common.collect.Iterables.filter;

/**
 * @author Vladislav.Soroka
 * @since 8/19/2015
 */
public class DependencyResolverImpl implements DependencyResolver {

  private static final boolean is4OrBetter = GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("4.0")) >= 0;
  private static final boolean isJavaLibraryPluginSupported = is4OrBetter ||
                                                              (GradleVersion.current().compareTo(GradleVersion.version("3.4")) >= 0);
  private static final boolean isDependencySubstitutionsSupported = isJavaLibraryPluginSupported ||
                                                                    (GradleVersion.current().compareTo(GradleVersion.version("2.5")) > 0);
  private static final boolean isArtifactResolutionQuerySupported = isDependencySubstitutionsSupported ||
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

  public Collection<ExternalDependency> resolveDependencies(@Nullable String configurationName, @Nullable String scope) {
    if (configurationName == null) return Collections.emptyList();
    return resolveDependencies(myProject.getConfigurations().findByName(configurationName), scope).getExternalDeps();
  }

  @Override
  public Collection<ExternalDependency> resolveDependencies(@Nullable Configuration configuration) {
    return resolveDependencies(configuration, null).getExternalDeps();
  }

  public ExternalDepsResolutionResult resolveDependencies(@Nullable Configuration configuration, @Nullable String scope) {
    if (configuration == null || configuration.getAllDependencies().isEmpty()) {
      return ExternalDepsResolutionResult.EMPTY;
    }

    final ExternalDepsResolutionResult result;

    if (!myIsPreview && isArtifactResolutionQuerySupported) {
      result = new ArtifactQueryResolver(configuration, scope, myProject, myDownloadJavadoc, myDownloadSources, mySourceSetFinder).resolve();
    } else {
      result = new ExternalDepsResolutionResult(findDependencies(configuration, configuration.getAllDependencies(), scope),
                                                new ArrayList<File>());
    }

    Set<ExternalDependency> fileDependencies = findAllFileDependencies(configuration.getAllDependencies(), scope);
    result.getExternalDeps().addAll(fileDependencies);
    return result;
  }


  protected static Multimap<ModuleComponentIdentifier, ProjectDependency> collectProjectDeps(@NotNull final Configuration configuration) {
    return projectDeps(configuration,
                       ArrayListMultimap.<ModuleComponentIdentifier, ProjectDependency>create(),
                       new HashSet<Configuration>());
  }

  private static Multimap<ModuleComponentIdentifier, ProjectDependency> projectDeps(Configuration conf,
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
      final Collection<File> resolvedFiles = getFiles(dep);
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
      Collection<ExternalDependency> dependencies = resolvedMap.get(getFiles(dep));
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
        resolvedRuntimeMap.put(getFiles(dep), dep);
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
      resolvedMap.put(getFiles(dep), dep);
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
            resolvedMap.put(getFiles(it), it);
          }
          result.addAll(providedDependencies);
        }
      }
    }

    for (Configuration cfg : providedConfigurations) {
      Collection<ExternalDependency> providedDependencies = resolveDependencies(cfg, providedScope).getExternalDeps();
      for (ExternalDependency dep : new DependencyTraverser(providedDependencies)) {
        Collection<ExternalDependency> dependencies = resolvedMap.get(getFiles(dep));
        if (!dependencies.isEmpty()) {
          if (dep.getDependencies().isEmpty()) {
            providedDependencies.remove(dep);
          }
          for (ExternalDependency depForScope: dependencies) {
            ((AbstractExternalDependency)depForScope).setScope(providedScope);
          }
        } else {
          resolvedMap.put(getFiles(dep), dep);
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
  static Collection<File> getFiles(ExternalDependency dependency) {
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

  public static ModuleComponentIdentifier toComponentIdentifier(ModuleVersionIdentifier id) {
    return new ModuleComponentIdentifierImpl(id.getGroup(), id.getName(), id.getVersion());
  }

  public static ModuleComponentIdentifier toComponentIdentifier(@NotNull String group, @NotNull String module, @NotNull String version) {
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

  private Set<ExternalDependency> findDependencies(@NotNull final  Configuration configuration,
                                                   @NotNull final  Collection<Dependency> dependencies,
                                                   @Nullable final String scope) {
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
          Set<File> artifacts = targetConfiguration.getAllArtifacts().getFiles().getFiles();
          projectDependency.setProjectDependencyArtifacts(artifacts);
          projectDependency.setProjectDependencyArtifactsSources(findArtifactSources(artifacts, mySourceSetFinder));

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

  public static Configuration getTargetConfiguration(ProjectDependency projectDependency) {

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


  @NotNull
  public static List<File> findArtifactSources(Collection<File> artifactFiles, SourceSetCachedFinder sourceSetFinder) {
    List<File> artifactSources = new ArrayList<File>();
    for (File artifactFile : artifactFiles) {
      SourceSet sourceSet = sourceSetFinder.findByArtifact(artifactFile.getPath());
      if (sourceSet != null) {
        artifactSources.addAll(sourceSet.getAllJava().getSrcDirs());
      }
    }
    return artifactSources;
  }

  public static boolean isProjectDependencyArtifact(ResolvedArtifact artifact) {
    return isDependencySubstitutionsSupported && artifact.getId().getComponentIdentifier() instanceof ProjectComponentIdentifier;
  }

  private static MyModuleIdentifier toMyModuleIdentifier(ModuleVersionIdentifier id) {
    return new MyModuleIdentifier(id.getName(), id.getGroup());
  }

  private static MyModuleIdentifier toMyModuleIdentifier(String name, String group) {
    return new MyModuleIdentifier(name, group);
  }
}
