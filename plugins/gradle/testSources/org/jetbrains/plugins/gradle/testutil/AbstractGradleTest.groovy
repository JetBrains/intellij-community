package org.jetbrains.plugins.gradle.testutil

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.externalSystem.service.project.ExternalLibraryPathTypeMapper
import com.intellij.openapi.externalSystem.service.project.ProjectStructureServices
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.action.AbstractGradleSyncTreeFilterAction
import com.intellij.openapi.externalSystem.service.project.change.AutoImporter
import org.jetbrains.plugins.gradle.config.GradleColorAndFontDescriptorsProvider
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import com.intellij.openapi.externalSystem.service.project.PlatformFacade
import com.intellij.openapi.externalSystem.model.project.change.ExternalProjectStructureChangesCalculator
import org.jetbrains.plugins.gradle.diff.contentroot.GradleContentRootStructureChangesCalculator
import org.jetbrains.plugins.gradle.diff.dependency.GradleLibraryDependencyStructureChangesCalculator
import org.jetbrains.plugins.gradle.diff.dependency.GradleModuleDependencyStructureChangesCalculator
import org.jetbrains.plugins.gradle.diff.library.GradleLibraryStructureChangesCalculator
import org.jetbrains.plugins.gradle.diff.module.GradleModuleStructureChangesCalculator
import org.jetbrains.plugins.gradle.diff.project.GradleProjectStructureChangesCalculator
import com.intellij.openapi.externalSystem.service.project.manage.EntityManageHelper
import com.intellij.openapi.externalSystem.service.project.manage.JarDataService
import com.intellij.openapi.externalSystem.service.project.manage.LibraryDataService
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataServiceImpl
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.project.LibraryData
import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import com.intellij.openapi.externalSystem.model.project.id.EntityIdMapper
import com.intellij.openapi.externalSystem.model.project.id.JarId
import com.intellij.openapi.externalSystem.model.project.id.LibraryId
import org.jetbrains.plugins.gradle.sync.GradleDuplicateLibrariesPreProcessor
import com.intellij.openapi.externalSystem.service.project.change.MovedJarsPostProcessor
import com.intellij.openapi.externalSystem.service.project.change.OutdatedLibraryVersionPostProcessor
import com.intellij.openapi.externalSystem.service.project.change.ProjectStructureChangesModel
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper
import com.intellij.openapi.externalSystem.ui.ExternalProjectStructureTreeModel
import com.intellij.openapi.externalSystem.ui.ExternalProjectStructureNodeFilter
import org.jetbrains.plugins.gradle.util.*
import org.junit.Before
import org.picocontainer.MutablePicoContainer
import org.picocontainer.defaults.DefaultPicoContainer

import static org.junit.Assert.fail

/**
 * @author Denis Zhdanov
 * @since 2/13/12 10:43 AM
 */
public abstract class AbstractGradleTest {

  ProjectStructureChangesModel changesModel
  ExternalProjectStructureTreeModel treeModel
  GradleProjectBuilder gradle
  IntellijProjectBuilder intellij
  ChangeBuilder changesBuilder
  ProjectStructureChecker treeChecker
  def container
  private Map<TextAttributesKey, ExternalProjectStructureNodeFilter> treeFilters = [:]

  @Before
  public void setUp() {
    // TODO den uncomment
    //gradle = new GradleProjectBuilder()
    //intellij = new IntellijProjectBuilder()
    //changesBuilder = new ChangeBuilder()
    //treeChecker = new ProjectStructureChecker()
    //container = new DefaultPicoContainer() {
    //  @Override
    //  Object getComponentInstance(Object componentKey) {
    //    def result = super.getComponentInstance(componentKey)
    //    if (result == null && componentKey instanceof String) {
    //      def clazz = Class.forName(componentKey)
    //      if (clazz != null) {
    //        result = super.getComponentInstance(clazz)
    //      }
    //    }
    //    result
    //  }
    //}
    //container.registerComponentInstance(Project, intellij.project)
    //container.registerComponentInstance(PlatformFacade, intellij.platformFacade as PlatformFacade)
    //container.registerComponentImplementation(ProjectStructureChangesModel)
    //container.registerComponentImplementation(ExternalProjectStructureTreeModel)
    //container.registerComponentImplementation(ProjectStructureHelper)
    //container.registerComponentImplementation(ExternalProjectStructureChangesCalculator, GradleProjectStructureChangesCalculator)
    //container.registerComponentImplementation(GradleModuleStructureChangesCalculator)
    //container.registerComponentImplementation(GradleContentRootStructureChangesCalculator)
    //container.registerComponentImplementation(GradleModuleDependencyStructureChangesCalculator)
    //container.registerComponentImplementation(GradleLibraryDependencyStructureChangesCalculator)
    //container.registerComponentImplementation(GradleLibraryStructureChangesCalculator)
    //container.registerComponentImplementation(EntityIdMapper)
    //container.registerComponentImplementation(ProjectStructureServices)
    //container.registerComponentImplementation(ExternalLibraryPathTypeMapper, TestExternalLibraryPathTypeMapper)
    //container.registerComponentImplementation(ExternalDependencyManager)
    //container.registerComponentImplementation(LibraryDataService)
    //container.registerComponentImplementation(JarDataService, TestExternalJarManager)
    //container.registerComponentImplementation(ProjectDataServiceImpl)
    //container.registerComponentImplementation(GradleDuplicateLibrariesPreProcessor)
    //container.registerComponentImplementation(MovedJarsPostProcessor, TestMovedJarsPostProcessor)
    //container.registerComponentImplementation(OutdatedLibraryVersionPostProcessor)
    //container.registerComponentImplementation(AutoImporter)
    //container.registerComponentImplementation(GradleSettings)
    //container.registerComponentImplementation(GradleLocalSettings)
    //container.registerComponentImplementation(EntityManageHelper)
    //configureContainer(container)
    //
    //intellij.projectStub.getComponent = { clazz -> container.getComponentInstance(clazz) }
    //intellij.projectStub.getPicoContainer = { container }
    //
    //changesModel = container.getComponentInstance(ProjectStructureChangesModel) as ProjectStructureChangesModel
    //
    //for (d in GradleColorAndFontDescriptorsProvider.DESCRIPTORS) {
    //  treeFilters[d.key] = AbstractGradleSyncTreeFilterAction.createFilter(d.key)
    //}
    //
    //def settings = ServiceManager.getService(intellij.project, GradleSettings.class)
    //settings.useAutoImport = false
  }

  protected void clearChangePostProcessors() {
    changesModel.commonPreProcessors.clear()
    changesModel.commonPostProcessors.clear()
  }

  protected void configureContainer(MutablePicoContainer container) {
  }

  @SuppressWarnings("GroovyAssignabilityCheck")
  protected def init(map = [:]) {
    treeModel = container.getComponentInstance(ExternalProjectStructureTreeModel) as ExternalProjectStructureTreeModel
    treeModel.processChangesAtTheSameThread = true;
    setState(map, false)
    treeModel.rebuild()
    changesModel.update(gradle.project)
  }

  protected def setState(map, update = true) {
    map.intellij?.delegate = intellij
    map.intellij?.call()
    map.gradle?.delegate = gradle
    map.gradle?.call()
    treeModel.changesComparator = map.changesSorter as Comparator
    if (update) {
      changesModel.update(gradle.project)
    }
  }

  protected def checkChanges(Closure c) {
    changesBuilder.changes.clear()
    c.delegate = changesBuilder
    def expected = c()
    if (!expected) {
      expected = [].toSet()
    }
    def actual = new HashSet(changesModel.changes)
    if (expected == actual) {
      return
    }
    actual.removeAll(expected)
    expected.removeAll(changesModel.changes)
    def message = "Project structure changes are mismatched."
    if (expected) {
      message += "\n  Expected but not matched:"
      expected.each { message += "\n    * $it"}
    }
    if (actual) {
      message += "\n  Unexpected:"
      actual.each { message += "\n    * $it"}
    }
    fail(message)
  }

  protected def checkTree(c) {
    def nodeBuilder = new NodeBuilder()
    c.delegate = nodeBuilder
    def expected = c()
    treeChecker.check(expected, treeModel.root)
  }

  protected static Closure changeByClassSorter(Map<Class<?>, Integer> rules) {
    { a, b ->
      def weightA = rules[a.class] ?: Integer.MAX_VALUE
      def weightB = rules[b.class] ?: Integer.MAX_VALUE
      if (weightA == weightB) {
        return  a.hashCode() - b.hashCode()
      }
      else {
        return weightA - weightB
      }
    }
  }

  protected def applyTreeFilter(@NotNull TextAttributesKey toShow) {
    treeModel.addFilter(treeFilters[toShow])
  }

  protected def resetTreeFilter(@NotNull TextAttributesKey filterKey) {
    treeModel.removeFilter(treeFilters[filterKey])
  }

  @NotNull
  protected JarId findJarId(@NotNull String path) {
    String pathToUse = GradleUtil.toCanonicalPath(path)
    for (LibraryData library in (gradle.libraries.values() as Collection<LibraryData>)) {
      for (libPath in library.getPaths(LibraryPathType.BINARY)) {
        if (libPath == pathToUse) {
          return new JarId(pathToUse, LibraryPathType.BINARY, new LibraryId(ProjectSystemId.GRADLE, library.name))
        }
      }
    }

    for (Library library in (intellij.libraries.values() as Collection<Library>)) {
      for (jarFile in library.getFiles(OrderRootType.CLASSES)) {
        if (pathToUse == jarFile.path) {
          return new JarId(pathToUse, LibraryPathType.BINARY, new LibraryId(ProjectSystemId.IDE, library.name))
        }
      }
    }

    String errorMessage = """
Can't build an id object for given jar path ($path).
  Available gradle libraries:
    ${gradle.libraries.values().collect { LibraryData lib -> "${lib.name}: ${lib.getPaths(LibraryPathType.BINARY)}" }.join('\n    ') }
  Available intellij libraries:
    ${intellij.libraries.values().collect { Library lib -> "${lib.name}: ${lib.getFiles(OrderRootType.CLASSES).collect{it.path}}" }
    .join('\n    ')}
"""

    throw new IllegalArgumentException(errorMessage)
  }

  @NotNull
  protected LibraryId findLibraryId(@NotNull String name, boolean gradleLibrary) {
    def libraries = gradleLibrary ? gradle.libraries : intellij.libraries
    def library = libraries[name]
    if (library == null) {
      def errorMessage =
        "Can't find ${gradleLibrary ? 'gradle' : 'ide'} library with name '$name'. Available libraries: ${libraries.keySet()}"
      throw new IllegalArgumentException(errorMessage)
    }
    new LibraryId(gradleLibrary ? ProjectSystemId.GRADLE : ProjectSystemId.IDE, name)
  }
}
