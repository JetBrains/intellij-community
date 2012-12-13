package org.jetbrains.plugins.gradle.sync;


import com.intellij.testFramework.SkipInHeadlessEnvironment
import org.jetbrains.plugins.gradle.diff.library.GradleJarPresenceChange
import org.jetbrains.plugins.gradle.diff.module.GradleModulePresenceChange
import org.jetbrains.plugins.gradle.testutil.AbstractGradleTest
import org.junit.Test

import static org.junit.Assert.assertEquals
import org.jetbrains.plugins.gradle.diff.dependency.GradleLibraryDependencyPresenceChange
import org.jetbrains.plugins.gradle.config.GradleTextAttributes

/**
 * @author Denis Zhdanov
 * @since 01/25/2012
 */
@SkipInHeadlessEnvironment
public class GradleProjectStructureChangesModelTest extends AbstractGradleTest {

  @Test
  public void "obsolete gradle-local modules"() {
    // Configure initial projects state.
    init(
      gradle: {
        project {
          module {
            dependencies {
              library("lib1")
              library("lib2")
              library("lib3")
        } } } },

      intellij: {
        project {
          module {
            dependencies {
              library("lib1")
        } } } },
      changesSorter: { a, b ->
        b.gradleEntity.dependencyName.compareTo(a.gradleEntity.dependencyName)
      }
    )
    
    // Check that the initial projects state is correctly parsed.
    checkChanges {
      presence {
        library(gradle: gradle.libraryDependencies.values().flatten().findAll { it.name == "lib2" })
        library(gradle: gradle.libraryDependencies.values().flatten().findAll { it.name == "lib3" })
    } }
    checkTree {
      project {
        module() {
          dependencies {
            lib2('gradle') // Gradle-local entities are on top
            lib3('gradle') // Gradle-local entities are on top
            lib1()
    } } } }

    // Add the same library at intellij side. Expecting to have the only change now.
    setState(intellij: {
      project {
        module {
          dependencies {
            library("lib1")
            library("lib2")
    } } } })
    
    checkChanges {
      presence {
        library(gradle: gradle.libraryDependencies.values().flatten().findAll { it.name == "lib3" })
    } }
    checkTree {
      project {
        module() {
          dependencies {
            lib3('gradle') // Gradle-local entities are on top
            lib1()
            lib2()
    } } } }
    
    // Remove the 'gradle local' dependency.
    setState(gradle: {
      project {
        module {
          dependencies {
            library("lib1")
            library("lib2")
    } } } })

    checkChanges { } // no changes.
    assertEquals([].toSet(), changesModel.changes)
    checkTree {
      project {
        module {
          dependencies {
            lib1()
            lib2()
    } } } }
  }
  
  @Test
  public void "library dependencies on binary paths"() {
    // Let the model has two differences in a library setup initially.
    init(
      gradle: {
        project {
          module {
            dependencies {
              library("lib1")
              library("lib2", bin: ['jar1', 'jar2']) } } } },
      intellij: {
        project {
          module {
            dependencies {
              library("lib1")
              library("lib2", bin: ['jar2', 'jar3']) } } } })

    checkChanges {
      presence {
        jar(gradle: [findJarId('jar1')])
        jar(intellij: [findJarId('jar3')])
      }
    }
    
    checkTree {
      project {
        module {
          dependencies {
            lib1()
            lib2 {
              jar1('gradle')
              jar2()
              jar3('intellij') } } } } }
    
    // Remove one difference from the library setup and check that the corresponding node is still marked as conflicted
    setState(
      gradle: {
        project {
          module {
            dependencies {
              library("lib1")
              library("lib2", bin: ['jar2']) } } } },
      intellij: {
        project {
          module {
            dependencies {
              library("lib1")
              library("lib2", bin: ['jar2', 'jar3']) } } } })
    
    checkChanges {
      presence {
        jar(intellij: [findJarId('jar3')])
      }
    }
    
    checkTree {
      project {
        module {
          dependencies {
            lib1()
            lib2 {
              jar2()
              jar3('intellij') } } } } }
    
    // Match the remaining change and check that the corresponding node is not marked as conflicted anymore.
    setState(
      gradle: {
        project {
          module {
            dependencies {
              library("lib1")
              library("lib2", bin: ['jar2', 'jar3'])
      } } } },
      intellij: {
        project {
          module {
            dependencies {
              library("lib1")
              library("lib2", bin: ['jar2', 'jar3']) } } } })
    checkChanges { } // No changes
    checkTree {
      project {
        module {
          dependencies {
            lib1()
            lib2 {
              jar2()
              jar3() } } } } }
  }

  @Test
  public void "gradle-local module which library setup differs from intellij when jar presence change goes first"() {
    init(
      gradle: {
        project {
          module('module1') {
            dependencies {
              library("lib1", bin: ['jar1', 'jar2']) }}
          module('module2') {
            dependencies {
              library("lib1", bin: ['jar1', 'jar2']) } } } },
      intellij: {
        project {
          module('module1') {
            dependencies {
              library("lib1", bin: ['jar2', 'jar3']) } } } },
      changesSorter: changeByClassSorter([
        (GradleJarPresenceChange)               : 1,
        (GradleLibraryDependencyPresenceChange) : 2,
        (GradleModulePresenceChange)            : 3,
      ])
    )

    checkChanges {
      presence {
        jar(gradle: [findJarId('jar1')])
        jar(intellij: [findJarId('jar3')])
        library(gradle: gradle.libraryDependencies[gradle.modules['module2']].find { it.name == "lib1" })
        module(gradle: gradle.modules['module2'])
      }
    }

    checkTree {
      project {
        module2('gradle') {
          dependencies {
            lib1('gradle') {
              jar1('gradle')
              jar2('gradle')} } }
        module1 {
          dependencies {
            lib1 {
              jar1('gradle')
              jar2()
              jar3('intellij') } } } } }
  }

  @Test
  public void "adding intellij-local jar"() {
    Closure initialProject = {
      project {
        module {
          dependencies {
            library('lib1', bin: ['jar1'])} } } }
    init(gradle: initialProject, intellij: initialProject)
    checkChanges {} // empty
    
    setState(
      intellij: {
        project {
          module {
            dependencies {
              library('lib1', bin: ['jar1', 'jar2'])
      } } } }
    )
    
    checkChanges {
      presence {
        jar(intellij: [findJarId('jar2')])
    } }
    checkTree {
      project {
        module {
          dependencies {
            lib1 {
              jar1()
              jar2('intellij')} } } } }
  }
  
  @Test
  public void "intellij module removal"() {
    Closure initialClosure = {
      project {
        module('module1')
        module('module2') {
          dependencies {
            library('lib1')
            library('lib2')
    } } } }
    init(gradle: initialClosure, intellij: initialClosure)
    checkChanges { } // No changes
    checkTree {
      project {
        module1()
        module2() {
          dependencies {
            lib1()
            lib2()
    } } } }
    
    setState(intellij: {
      project {
        module('module1')
    } } )
    checkChanges {
      presence {
        module(gradle: gradle.modules['module2'])
        libraryDependency(gradle: gradle.modules['module2'].dependencies)
    } }
    checkTree {
      project {
        module2('gradle') {
          dependencies {
            lib1('gradle')
            lib2('gradle')
        } }
        module1()
    } }
  }
  
  @Test
  public void "gradle-local module is not treated as 'local' after import"() {
    init(
      gradle: {
        project {
          module('module1')
          module('module2')
      } },
      intellij: {
        project {
          module('module1')
      } }
    )
    checkChanges {
      presence {
        module(gradle: gradle.modules['module2'])
    } }
    checkTree {
      project {
        module2('gradle')
        module1()
    } }
    
    // Emulate import gradle module to intellij.
    setState(intellij: {
      project {
        module('module1')
        module('module2')
    } })
    checkChanges { } // No changes
    checkTree {
      project {
        module1()
        module2() // Imported module node is not highlighted anymore.
    } }
  }
  
  @Test
  public void "gradle local library dependency outweighs library path conflict"() {
    init(
      gradle: {
        project {
          module('module1') {
            dependencies {
              library('lib1', bin: ['jar1']) } }
          module('module2') {
            dependencies {
              library('lib1') } } } },
      intellij: {
        project {
          module('module1') {
            dependencies {
              library('lib1', bin: ['jar2']) } } } },
      changesSorter: changeByClassSorter([
        (GradleModulePresenceChange)            : 1,
        (GradleLibraryDependencyPresenceChange) : 2,
        (GradleJarPresenceChange)               : 3,
      ])
    )

    checkChanges {
      presence {
        module(gradle: gradle.modules['module2'])
        jar(gradle: [findJarId('jar1')])
        jar(intellij: [findJarId('jar2')])
        libraryDependency(gradle: gradle.libraryDependencies[gradle.modules['module2']].first())
      }
    }
    
    checkTree {
      project {
        module2('gradle') {
          dependencies {
            lib1('gradle') {
              jar1('gradle') } } }
        module1() {
          dependencies {
            lib1 {
              jar1('gradle')
              jar2('intellij') } } } } }
  }
  
  @Test
  public void "mismatched module dependency import"() {
    init(
      gradle: {
        project {
          module('module1')
          module('module2')
          module('module3') {
            dependencies {
              module('module1')
              module('module2')
      } } } },
      intellij: {
        project {
          module('module2')
          module('module4')
          module('module3') {
            dependencies {
              module('module2')
              module('module4')
      } } } }
    )
    checkChanges {
      presence {
        module(gradle: gradle.modules['module1'])
        module(intellij: intellij.modules['module4'])
        moduleDependency(gradle: gradle.moduleDependencies[gradle.modules['module3']].find { it.target == gradle.modules['module1']})
        moduleDependency(intellij: intellij.moduleDependencies[intellij.modules['module3']].find { it.moduleName == 'module4'})
    } }
    checkTree {
      project {
        module1('gradle')
        module2()
        module3() {
          dependencies {
            module1('gradle')
            module2()
            module4('intellij')
        } }
        module4('intellij')
    } }
    
    Closure newProjectState = {
      project {
        module('module1')
        module('module2')
        module('module4')
        module('module3') {
          dependencies {
            module('module1')
            module('module2')
            module('module4')
    } } } }
    setState(gradle: newProjectState, intellij: newProjectState)
    checkChanges { } // No changes
    checkTree {
      project {
        module1()
        module2()
        module3 {
          dependencies {
            module1()
            module2()
            module4()
         } }
        module4()
    } }
  }
  
  @Test
  public void "mismatched module dependency removal"() {
    init(
      gradle: {
        project {
          module('module1')
          module('module2')
          module('module3') {
            dependencies {
              module('module1')
              module('module2')
      } } } },
      intellij: {
        project {
          module('module2')
          module('module4')
          module('module3') {
            dependencies {
              module('module2')
              module('module4')
      } } } }
    )
    checkChanges {
      presence {
        module(gradle: gradle.modules['module1'])
        module(intellij: intellij.modules['module4'])
        moduleDependency(gradle: gradle.moduleDependencies[gradle.modules['module3']].find { it.target == gradle.modules['module1']})
        moduleDependency(intellij: intellij.moduleDependencies[intellij.modules['module3']].find { it.moduleName == 'module4'})
    } }
    checkTree {
      project {
        module1('gradle')
        module2()
        module3() {
          dependencies {
            module1('gradle')
            module2()
            module4('intellij')
        } }
        module4('intellij')
    } }

    Closure newProjectState = {
      project {
        module('module2')
        module('module3') {
          dependencies {
            module('module2')
    } } } }
    setState(gradle: newProjectState, intellij: newProjectState)
    checkChanges { } // No changes
    checkTree {
      project {
        module2()
        module3 {
          dependencies {
            module2()
    } } } }
  }
  
  @Test
  public void "cycled module dependencies"() {
    init(
      gradle: {
        project {
          module('module1') {
            dependencies {
              module('module2')
          } }
          module('module2') {
            dependencies {
              module('module1')
      } } } },
      intellij: {
        project {
          module('module2') {
            dependencies {
              module('module3')
          } }
          module('module3') {
            dependencies {
              module('module2')
      } } } } 
    )
    checkChanges {
      presence {
        module(gradle: gradle.modules['module1'])
        module(intellij: intellij.modules['module3'])
        moduleDependency(gradle: gradle.moduleDependencies.values().flatten())
        moduleDependency(intellij: intellij.moduleDependencies.values().flatten())
    } }
    checkTree {
      project {
        module1('gradle') {
          dependencies {
            module2('gradle')
        } }
        module2() {
          dependencies {
            module1('gradle')
            module3('intellij')
        } }
        module3('intellij') {
          dependencies {
            module2('intellij')
    } } } }
  }
  
  @Test
  public void "local content root importing"() {
    init(
      gradle: {
        project {
          module {
            contentRoot('1')
            contentRoot('2')
      } } },
      intellij: {
        project {
          module {
            contentRoot('2')
            contentRoot('3')
      } } }
    )
    checkChanges {
      presence {
        contentRoot(gradle: gradle.contentRoots.values().flatten().find { it.rootPath.endsWith('1') })
        contentRoot(intellij: intellij.contentRoots.values().flatten().find { it.file.path.endsWith('3') })
    } }
    checkTree {
      project {
        module {
          "content-root:1"('gradle')
          "content-root:2"()
          "content-root:3"('intellij')
    } } }
    
    // Import local content roots.
    Closure projectState = {
      project {
        module {
          contentRoot('1')
          contentRoot('2')
          contentRoot('3')
    } } }
    setState(intellij: projectState, gradle: projectState)
    checkChanges { } // No changes
    checkTree {
      project {
        module {
          "content-root:1"()
          "content-root:2"()
          "content-root:3"()
    } } }
  }
  
  @Test
  public void "module removal at intellij"() {
    Closure initial = {
      project {
        module {
          contentRoot('1')
          dependencies {
            library('lib1')
    } } } }
    init(gradle: initial, intellij: initial)
    checkChanges { }
    checkTree {
      project {
        module {
          "content-root"()
          dependencies {
            lib1()
    } } } }
    
    setState(intellij: { project { }})
    def m = gradle.modules.values().flatten().first()
    checkChanges {
      presence {
        module(gradle: m)
        contentRoot(gradle: gradle.contentRoots[m])
        libraryDependency(gradle: gradle.libraryDependencies[m])
    } }
    checkTree {
      project {
        module('gradle') {
          "content-root"('gradle')
          dependencies {
            lib1('gradle')
    } } } }
  }

  @Test
  public void "content root is correctly highlighted after importing gradle local module"() {
    Closure completeProject = {
      project {
        module {
          contentRoot('1')
    } } }
    init(gradle: completeProject, intellij: { project { }})
    def m = gradle.modules.values().flatten().first()
    checkChanges {
      presence {
        module(gradle: m)
        contentRoot(gradle: gradle.contentRoots[m])
    } }
    checkTree {
      project {
        module('gradle') {
          "content-root"('gradle')
    } } }
    
    // Import the whole module.
    setState(gradle: completeProject, intellij: completeProject)
    checkChanges { }
    checkTree {
      project {
        module() {
          "content-root"()
    } } }
  }

  @Test
  public void "filter new gradle local changes"() {
    Closure initial = {
      project {
        module('module1') {
    } } }
    init(gradle: initial, intellij: initial)

    // Apply filter.
    applyTreeFilter(GradleTextAttributes.CHANGE_CONFLICT)
    checkTree {
      project {
    } }

    // Introduce gradle-local entities.
    setState(gradle: {
      project {
        module('module1')
        module('module2') {
          contentRoot('1')
          dependencies {
            library('lib1')
            module('module1')
    } } } })

    // Ensure that gradle-local entities have not been picked up.
    checkTree {
      project {
    } }
  }

  @Test
  public void "filter new conflict changes"() {
    Closure initial = {
      project {
        module('module1')
        module('module2') {
          dependencies {
            library('lib1', bin: ['1'])
            module('module1', scope: 'compile')
    } } } }
    init(gradle: initial, intellij: initial)

    // Apply filter.
    applyTreeFilter(GradleTextAttributes.GRADLE_LOCAL_CHANGE)
    checkTree {
      project {
    } }
    
    // Introduce conflict changes.
    setState(gradle: {
      project {
        module('module1')
        module('module2') {
          dependencies {
            library('lib1', bin: ['2'])
            module('module1', scope: 'test')
    } } } })

    // Ensure that conflict changes have not been processed.
    checkTree {
      project {
    } }
  }
  
  @Test
  public void "filter obsolete gradle local changes"() {
    Closure completeProject = {
      project {
        module('module1')
        module('module2') {
          contentRoot('1')
          dependencies {
            module('module1')
            library('lib1')
    } } } }
    init (
      gradle: completeProject,
      intellij: {
        project {
      } }
    )
    
    applyTreeFilter(GradleTextAttributes.GRADLE_LOCAL_CHANGE)
    checkTree {
      project {
        module1('gradle')
        module2('gradle') {
          "content-root"('gradle')
          dependencies {
            module1('gradle')
            lib1('gradle')
    } } } }
    
    setState(intellij: completeProject)
    checkTree {
      project {
    } }
  }

  @Test
  public void "filter obsolete conflict changes"() {
    Closure gradleProject = {
      project {
        module('module1')
        module('module2') {
          dependencies {
            library('lib1', bin: ['1'])
            module('module1', scope: 'compile')
    } } } }
    init(gradle: gradleProject, intellij: {
      project {
        module('module1')
        module('module2') {
          dependencies {
            library('lib1', bin: ['2'])
            module('module1', scope: 'test')
    } } } })
    
    applyTreeFilter(GradleTextAttributes.CHANGE_CONFLICT)
    checkTree {
      project {
        module2 {
          dependencies {
            module1('conflict')
    } } } }
    
    setState(intellij: gradleProject)
    checkTree {
      project {
    } }
  }
}