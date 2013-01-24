/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.sync

import org.jetbrains.plugins.gradle.config.GradleTextAttributes
import org.jetbrains.plugins.gradle.testutil.AbstractGradleTest
import org.junit.Test

/**
 * @author Denis Zhdanov
 * @since 1/22/13 3:04 PM
 */
class GradleOutdatedLibraryVersionTest extends AbstractGradleTest {
  
  @Test
  void "changed library version change construction and representation"() {
    def gradleProject = {
      project {
        module('module1') {
          dependencies {
            library('lib-2', bin: ['jar1-2', 'jar2-2']) } }
        module('module2') {
          dependencies {
            library('lib-2', bin: ['jar1-2', 'jar2-2']) } } } }
    
    init(
      gradle: gradleProject,
              
      intellij: {
        project {
          module('module1') {
            dependencies {
              library('lib-1', bin: ['jar1-1', 'jar2-1']) } }
          module('module2') {
            dependencies {
              library('lib-1', bin: ['jar1-1', 'jar2-1']) } } }  
      }        
    )
    
    checkChanges {
      libraryVersion (
        name: 'lib',
        gradleVersion: '2',
        gradleLibraryId: findLibraryId('lib-2', true),
        ideVersion: '1',
        ideLibraryId: findLibraryId('lib-1', false)
      )
    }
    
    checkTree {
      project {
        module1 {
          dependencies {
            'lib (1 -> 2)' ('outdated') } }
        module2 {
          dependencies {
            'lib (1 -> 2)' ('outdated') } } } }
    
    // Emulate 'sync library' and check how obsolete change is processed.
    setState(intellij: gradleProject)
    checkChanges { } // No changes
    checkTree {
      project {
        module1 {
          dependencies {
            'lib-2' {
              'jar1-2'()
              'jar2-2'() } } }
        module2 {
          dependencies {
            'lib-2' {
              'jar1-2'()
              'jar2-2'() } } } } }
  }
  
  @Test
  void "'outdated' filter processing"() {
    init(
      gradle: {
        project {
          module('module1') {
            dependencies {
              library('lib-2', bin: ['jar1-2', 'jar2-2'])
              library('same-lib') } }
          module('module2') {
            dependencies {
              library('lib-2', bin: ['jar1-2', 'jar2-2']) } } } },
              
      intellij: {
        project {
          module('module1') {
            dependencies {
              library('lib-1', bin: ['jar1-1', 'jar2-1'])
              library('same-lib')} }
          module('module2') {
            dependencies {
              library('lib-1', bin: ['jar1-1', 'jar2-1']) } } } }        
    )

    applyTreeFilter(GradleTextAttributes.OUTDATED_ENTITY)
    checkTree {
      project {
        module1 {
          dependencies {
            'lib (1 -> 2)' ('outdated')
        } }
        module2 {
          dependencies {
            'lib (1 -> 2)' ('outdated') } } } }
    
    resetTreeFilter(GradleTextAttributes.OUTDATED_ENTITY)
    checkTree {
      project {
        module1 {
          dependencies {
            'lib (1 -> 2)' ('outdated')
            'same-lib'()
        } }
        module2 {
          dependencies {
            'lib (1 -> 2)' ('outdated') } } } }
  }
}
