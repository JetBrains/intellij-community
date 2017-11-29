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
package com.intellij.util.concurrency

import com.intellij.testFramework.PlatformTestCase

class QueueProcessorTest : PlatformTestCase() {
  fun `test waiting for returns on finish condition`() {
    var stop = false;
    val semaphore = Semaphore(0)
    val processor = QueueProcessor<Any>({ semaphore.down() }, { stop })
    
    processor.add(1)
    stop = true;
    semaphore.up()
    
    assertTrue(processor.waitFor(1000));
    processor.waitFor(); // just in case let's check this method as well - hopefully, it won't hang since waitFor(timeout) works
  }
}