/*
 * Copyright 2000-2024 JetBrains s.r.o.
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
package git4idea.tests

import git4idea.commands.GitStandardProgressAnalyzer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * @author Kirill Likhodedov
 */
class GitStandardProgressAnalyzerTest {

  private lateinit var myProgressModifier: GitStandardProgressAnalyzer

  @BeforeEach
  fun setUp() {
    myProgressModifier = GitStandardProgressAnalyzer()
  }

  @Test
  fun returnMinusOneOnNonMatch() {
    assertEquals(-1.0, myProgressModifier.analyzeProgress("From git@github.com:idea/community"), EPS)
  }

  @Test
  fun countingObjects() {
    assertEquals(0.05 * (3178 / 5000.0), myProgressModifier.analyzeProgress("remote: Counting objects: 3178"), EPS)
  }

  @Test
  fun compressingObjects() {
    assertEquals(0.05 + 0.1 * 0.34, myProgressModifier.analyzeProgress("remote: Compressing objects: 34% (289/850)"), EPS)
  }

  @Test
  fun receivingObjects() {
    assertEquals(0.15 + 0.8 * 0.7, myProgressModifier.analyzeProgress("remote: Receiving objects: 70% (595/850), 4.18 MiB | 223 KiB/s"),
                 EPS)
  }

  @Test
  fun writingObjects() {
    assertEquals(0.15 + 0.8 * 0.6, myProgressModifier.analyzeProgress("Writing objects:  60% (3/5), 49.91 MiB | 422 KiB/s"),
                 EPS)
  }

  @Test
  fun resolvingDeltas() {
    assertEquals(0.95 + 0.05 * 0.34, myProgressModifier.analyzeProgress("remote: Resolving deltas: 34% (289/850)"), EPS)
  }

  @Test
  fun testAllAtOnce() {
    assertEquals(-1.0, myProgressModifier.analyzeProgress("Cloning into nb1..."), EPS)
    // remote: Counting objects: 1345
    for (i in 0..3177) {
      val message = countingObjects(i, false)
      assertEquals(0.05 * (i / 5000.0), myProgressModifier.analyzeProgress(message), EPS, message)
    }
    // remote: Counting objects: 3178, done.
    assertEquals(0.05 * (3718 / 5000.0), myProgressModifier.analyzeProgress(countingObjects(3718, true)), EPS)
    // remote: Compressing objects: 34% (289/850)
    // remote: Compressing objects: 100% (850/850), done.
    for (i in 0..100) {
      val message = compressingObjects(i)
      assertEquals(0.05 + 0.1 * (i / 100.0), myProgressModifier.analyzeProgress(message), EPS, message)
    }
    assertEquals(-1.0, myProgressModifier.analyzeProgress("    remote: Total 3178 (delta 1822), reused 3161 (delta 1815)"))
    // Receiving objects: 34% (289/850) , 4.18 MiB | 144 KiB/s
    for (i in 0..100) {
      val message = receivingObjects(i)
      assertEquals(0.15 + 0.8 * (i / 100.0), myProgressModifier.analyzeProgress(message), EPS, message)
    }
    // Resolving deltas: 100% (1822/1822), done.
    for (i in 0..100) {
      val message = resolvingDeltas(i)
      assertEquals(0.95 + 0.05 * (i / 100.0), myProgressModifier.analyzeProgress(message), EPS, message)
    }
  }

  companion object {
    private const val EPS = 0.00001

    private fun countingObjects(objectsCounted: Int, done: Boolean): String {
      return "remote: Counting objects: $objectsCounted${doneStr(done)}"
    }

    private fun compressingObjects(percent: Int): String {
      val objects = 850 * percent / 100
      return "remote: Compressing objects: $percent% ($objects/850)${doneStr(percent == 100)}"
    }

    private fun receivingObjects(percent: Int): String {
      val objects = 3178 * percent / 100
      val speed = "4.18 MiB | 223 KiB/s"
      return "Receiving objects: $percent% ($objects/3178), $speed${doneStr(percent == 100)}"
    }

    private fun resolvingDeltas(percent: Int): String {
      val total = 1822
      val objects = total * percent / 100
      return "Resolving deltas: $percent% ($objects/$total)${doneStr(percent == 100)}"
    }

    private fun doneStr(done: Boolean): String {
      return if (done) ", done." else ""
    }
  }
}
