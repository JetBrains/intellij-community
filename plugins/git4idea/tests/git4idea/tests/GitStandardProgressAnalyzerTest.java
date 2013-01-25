/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.tests;

import git4idea.commands.GitStandardProgressAnalyzer;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.testng.Assert.assertEquals;

/**
 * @author Kirill Likhodedov
 */
public class GitStandardProgressAnalyzerTest {

  private static final double EPS = 0.00001;
  private GitStandardProgressAnalyzer myProgressModifier;

  @Before
  public void setUp() {
    myProgressModifier = new GitStandardProgressAnalyzer();
  }

  @Test
  public void returnMinusOneOnNonMatch() {
    assertEquals(myProgressModifier.analyzeProgress("From git@github.com:idea/community"), -1.0, EPS);
  }

  @Test
  public void countingObjects() {
    assertEquals(myProgressModifier.analyzeProgress("remote: Counting objects: 3178"), 0.05 * (3178 / 5000.0), EPS);
  }

  @Test
  public void compressingObjects() {
    assertEquals(myProgressModifier.analyzeProgress("remote: Compressing objects: 34% (289/850)"), 0.05 + 0.1 * 0.34, EPS);
  }

  @Test
  public void recevingObjects() {
    assertEquals(myProgressModifier.analyzeProgress("remote: Receiving objects: 70% (595/850), 4.18 MiB | 223 KiB/s"),
                 0.15 + 0.8 * 0.7, EPS);
  }

  @Test
  public void writingObjects() {
    assertEquals(myProgressModifier.analyzeProgress("Writing objects:  60% (3/5), 49.91 MiB | 422 KiB/s"),
                 0.15 + 0.8 * 0.6, EPS);
  }

  @Test
  public void resolvingDeltas() {
    assertEquals(myProgressModifier.analyzeProgress("remote: Resolving deltas: 34% (289/850)"), 0.95 + 0.05 * 0.34, EPS);
  }

  @Test
  public void testAllAtOnce() throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
    assertEquals(myProgressModifier.analyzeProgress("Cloning into nb1..."), -1.0, EPS);
    // remote: Counting objects: 1345
    for (int i = 0; i < 3178; i++) {
      final String s = countingObjects(i, false);
      assertEquals(myProgressModifier.analyzeProgress(s), 0.05 * (i / 5000.0), EPS, s);
    }
    // remote: Counting objects: 3178, done.
    assertEquals(myProgressModifier.analyzeProgress(countingObjects(3718, true)), 0.05 * (3718 / 5000.0), EPS);
    // remote: Compressing objects: 34% (289/850)
    // remote: Compressing objects: 100% (850/850), done.
    for (int i = 0; i <= 100; i++) {
      final String s = compressingObjects(i);
      assertEquals(myProgressModifier.analyzeProgress(s), 0.05 + 0.1 * (i / 100.0), EPS, s);
    }
    myProgressModifier.analyzeProgress("    remote: Total 3178 (delta 1822), reused 3161 (delta 1815)");
    // Receiving objects: 34% (289/850) , 4.18 MiB | 144 KiB/s
    for (int i = 0; i <= 100; i++) {
      final String s = receivingObjects(i);
      assertEquals(myProgressModifier.analyzeProgress(s), 0.15 + 0.8 * (i / 100.0), EPS, s);
    }
    // Resolving deltas: 100% (1822/1822), done.
    for (int i = 0; i <= 100; i++) {
      final String s = resolvingDeltas(i);
      assertEquals(myProgressModifier.analyzeProgress(s), 0.95 + 0.05 * (i / 100.0), EPS, s);
    }
    assertEquals(getCurrentTotalProgressAtTheEnd(), 1.0, EPS);
  }

  private double getCurrentTotalProgressAtTheEnd() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    Class operationClass = null;
    for (Class cl : GitStandardProgressAnalyzer.class.getDeclaredClasses()) {
      if (cl.getName().endsWith("Operation")) {
        operationClass = cl;
      }
    }
    Object resolvingDeltasOperation = null;
    assert operationClass != null;
    for (Object enumConstant : operationClass.getEnumConstants()) {
      if (enumConstant.toString() == "RESOLVING_DELTAS") {
        resolvingDeltasOperation = enumConstant;
      }
    }

    Method totalProgress = GitStandardProgressAnalyzer.class.getDeclaredMethod("updateTotalProgress", operationClass);
    totalProgress.setAccessible(true);
    return (Double)totalProgress.invoke(myProgressModifier, resolvingDeltasOperation);
  }

  private static String countingObjects(int objectsCounted, boolean done) {
    return "remote: Counting objects: " + objectsCounted + doneStr(done);
  }

  private static String compressingObjects(int percent) {
    int objects = 850 * percent / 100;
    return "remote: Compressing objects: " + percent + "% (" + objects + "/850)" + doneStr(percent == 100);
  }

  private static String receivingObjects(int percent) {
    int objects = 3178 * percent / 100;
    String speed = "4.18 MiB | 223 KiB/s";
    return "Receiving objects: " + percent + "% (" + objects + "/3178), " + speed + doneStr(percent == 100);
  }

  private static String resolvingDeltas(int percent) {
    final int total = 1822;
    int objects = total * percent / 100;
    return "Resolving deltas: " + percent + "% (" + objects + "/" + total + ")" + doneStr(percent == 100);
  }

  private static String doneStr(boolean done) {
    return done ? ", done." : "";
  }
}
