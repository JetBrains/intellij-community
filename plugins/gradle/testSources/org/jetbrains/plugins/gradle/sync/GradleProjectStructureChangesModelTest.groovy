package org.jetbrains.plugins.gradle.sync;


import com.intellij.testFramework.SkipInHeadlessEnvironment
import org.jetbrains.plugins.gradle.testutil.AbstractGradleTest
import org.junit.Test

import static org.junit.Assert.assertEquals
import org.jetbrains.plugins.gradle.diff.GradleMismatchedLibraryPathChange
import org.jetbrains.plugins.gradle.diff.GradleLibraryDependencyPresenceChange

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
        } } } }
    )
    
    // Check that the initial projects state is correctly parsed.
    checkChanges {
      presence {
        library(gradle: gradle.modules.dependencies.flatten().findAll { it.name == "lib2" })
        library(gradle: gradle.modules.dependencies.flatten().findAll { it.name == "lib3" })
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
        library(gradle: gradle.modules.dependencies.flatten().findAll { it.name == "lib3" })
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
              library("lib2", bin: ['1', '2'])
      } } } },
      intellij: {
        project {
          module {
            dependencies {
              library("lib1")
              library("lib2", bin: ['2', '3'])
      } } } }
    )

    checkChanges {
      libraryConflict(entity: intellij.libraries['lib2']) {
        binaryPath(gradle: '1', intellij: ['3'])
    } }
    checkTree {
      project {
        module {
          dependencies {
            lib1()
            lib2('conflict')
    } } } }
    
    // Remove one difference from the library setup and check that the corresponding node is still marked as conflicted
    setState(
      gradle: {
        project {
          module {
            dependencies {
              library("lib1")
              library("lib2", bin: ['2'])
      } } } },
      intellij: {
        project {
          module {
            dependencies {
              library("lib1")
              library("lib2", bin: ['2', '3'])
      } } } }
    )
    checkChanges {
      libraryConflict(entity: intellij.dependencies.values().flatten().find {it.library.name == 'lib2' }.library) {
        binaryPath(gradle: null, intellij: '3')
    } }
    checkTree {
      project {
        module {
          dependencies {
            lib1()
            lib2('conflict')
    } } } }
    
    // Match the remaining change and check that the corresponding node is not marked as conflicted anymore.
    setState(
      gradle: {
        project {
          module {
            dependencies {
              library("lib1")
              library("lib2", bin: ['2', '3'])
      } } } },
      intellij: {
        project {
          module {
            dependencies {
              library("lib1")
              library("lib2", bin: ['2', '3'])
      } } } }
    )
    checkChanges { } // No changes
    checkTree {
      project {
        module {
          dependencies {
            lib1()
            lib2()
    } } } }
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
        module(gradle: gradle.modules.find { it.name == 'module2'})
        libraryDependency(gradle: gradle.modules.dependencies.flatten())
    } }
    checkTree {
      project {
        module1()
        module2('gradle') {
          dependencies {
            lib1('gradle')
            lib2('gradle')
    } } } }
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
        module(gradle: gradle.modules.find { it.name == 'module2' })
    } }
    checkTree {
      project {
        module1()
        module2('gradle')
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
              library('lib1', bin: ['1'])
          } }
          module('module2') {
            dependencies {
              library('lib1')
      } } } },
      intellij: {
        project {
          module('module1') {
            dependencies {
              library('lib1', bin: ['2'])
      } } } },
      changesSorter: changeByClassSorter([
        (GradleMismatchedLibraryPathChange)     : 2,
        (GradleLibraryDependencyPresenceChange) : 1
      ])
    )

    checkChanges {
      presence {
        module(gradle: gradle.modules.find { it.name == 'module2' })
        libraryDependency(gradle: gradle.dependencies[gradle.modules.find { it.name == 'module2' }].first())
      }
      libraryConflict(entity: intellij.libraries['lib1']) {
        binaryPath(gradle: ['1'], intellij: ['2'])
    } }
    checkTree {
      project {
        module1() {
          dependencies {
            lib1('conflict')
        } }
        module2('gradle') {
          dependencies {
            lib1('gradle') // This is the point of the test. We don't expect to see 'conflict' here.
    } } } }
  }
}
