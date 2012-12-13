package org.jetbrains.plugins.gradle.testutil

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.gradle.action.AbstractGradleSyncTreeFilterAction
import org.jetbrains.plugins.gradle.config.GradleColorAndFontDescriptorsProvider
import org.jetbrains.plugins.gradle.config.PlatformFacade
import org.jetbrains.plugins.gradle.diff.GradleStructureChangesCalculator
import org.jetbrains.plugins.gradle.diff.contentroot.GradleContentRootStructureChangesCalculator
import org.jetbrains.plugins.gradle.diff.dependency.GradleLibraryDependencyStructureChangesCalculator
import org.jetbrains.plugins.gradle.diff.dependency.GradleModuleDependencyStructureChangesCalculator
import org.jetbrains.plugins.gradle.diff.library.GradleLibraryStructureChangesCalculator
import org.jetbrains.plugins.gradle.diff.module.GradleModuleStructureChangesCalculator
import org.jetbrains.plugins.gradle.diff.project.GradleProjectStructureChangesCalculator
import org.jetbrains.plugins.gradle.model.GradleEntityOwner
import org.jetbrains.plugins.gradle.model.gradle.GradleLibrary
import org.jetbrains.plugins.gradle.model.gradle.LibraryPathType
import org.jetbrains.plugins.gradle.model.id.GradleEntityIdMapper
import org.jetbrains.plugins.gradle.model.id.GradleJarId
import org.jetbrains.plugins.gradle.model.id.GradleLibraryId
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureChangesModel
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureHelper
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureTreeModel
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNodeFilter
import org.jetbrains.plugins.gradle.util.GradleProjectStructureContext
import org.jetbrains.plugins.gradle.util.GradleUtil
import org.junit.Before
import org.picocontainer.MutablePicoContainer
import org.picocontainer.defaults.DefaultPicoContainer

import static org.junit.Assert.fail

/**
 * @author Denis Zhdanov
 * @since 2/13/12 10:43 AM
 */
public abstract class AbstractGradleTest {
  
  GradleProjectStructureChangesModel changesModel
  GradleProjectStructureTreeModel treeModel
  GradleProjectBuilder gradle
  IntellijProjectBuilder intellij
  def changesBuilder
  ProjectStructureChecker treeChecker
  def container
  private Map<TextAttributesKey, GradleProjectStructureNodeFilter> treeFilters = [:]

  @Before
  public void setUp() {
    gradle = new GradleProjectBuilder()
    intellij = new IntellijProjectBuilder()
    changesBuilder = new ChangeBuilder()
    treeChecker = new ProjectStructureChecker()
    container = new DefaultPicoContainer()
    container.registerComponentInstance(Project, intellij.project)
    container.registerComponentInstance(PlatformFacade, intellij.platformFacade as PlatformFacade)
    container.registerComponentImplementation(GradleProjectStructureChangesModel)
    container.registerComponentImplementation(GradleProjectStructureTreeModel)
    container.registerComponentImplementation(GradleProjectStructureHelper)
    container.registerComponentImplementation(GradleStructureChangesCalculator, GradleProjectStructureChangesCalculator)
    container.registerComponentImplementation(GradleModuleStructureChangesCalculator)
    container.registerComponentImplementation(GradleContentRootStructureChangesCalculator)
    container.registerComponentImplementation(GradleModuleDependencyStructureChangesCalculator)
    container.registerComponentImplementation(GradleLibraryDependencyStructureChangesCalculator)
    container.registerComponentImplementation(GradleLibraryStructureChangesCalculator)
    container.registerComponentImplementation(GradleEntityIdMapper)
    container.registerComponentImplementation(GradleProjectStructureContext)
    configureContainer(container)

    changesModel = container.getComponentInstance(GradleProjectStructureChangesModel) as GradleProjectStructureChangesModel
    
    for (d in GradleColorAndFontDescriptorsProvider.DESCRIPTORS) {
      treeFilters[d.key] = AbstractGradleSyncTreeFilterAction.createFilter(d.key)
    }
  }

  protected void configureContainer(MutablePicoContainer container) {
  }
  
  @SuppressWarnings("GroovyAssignabilityCheck")
  protected def init(map = [:]) {
    treeModel = container.getComponentInstance(GradleProjectStructureTreeModel) as GradleProjectStructureTreeModel
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

  protected def applyTreeFilter(TextAttributesKey toShow) {
    treeModel.addFilter(treeFilters[toShow])
  }
  
  @NotNull
  protected GradleJarId findJarId(@NotNull String path) {
    String pathToUse = GradleUtil.toCanonicalPath(path)
    for (GradleLibrary library in (gradle.libraries.values() as Collection<GradleLibrary>)) {
      for (libPath in library.getPaths(LibraryPathType.BINARY)) {
        if (libPath == pathToUse) {
          return new GradleJarId(pathToUse, new GradleLibraryId(GradleEntityOwner.GRADLE, library.name))
        }
      }
    }

    for (Library library in (intellij.libraries.values() as Collection<Library>)) {
      for (jarFile in library.getFiles(OrderRootType.CLASSES)) {
        if (pathToUse == jarFile.path) {
          return new GradleJarId(pathToUse, new GradleLibraryId(GradleEntityOwner.INTELLIJ, library.name))
        }
      }
    }
    
    String errorMessage = """
Can't build an id object for given jar path ($path).
  Available gradle libraries:
    ${gradle.libraries.values().collect { GradleLibrary lib -> "${lib.name}: ${lib.getPaths(LibraryPathType.BINARY)}" }.join('\n    ') }
  Available intellij libraries:
    ${intellij.libraries.values().collect { Library lib -> "${lib.name}: ${lib.getFiles(OrderRootType.CLASSES).collect{it.path}}" }
    .join('\n    ')}
"""
    
    throw new IllegalArgumentException(errorMessage)
  }
}
