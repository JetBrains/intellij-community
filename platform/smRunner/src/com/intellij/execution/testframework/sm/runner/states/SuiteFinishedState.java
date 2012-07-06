/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.execution.testframework.sm.runner.states;

import com.intellij.execution.testframework.CompositePrintable;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.sm.SMTestsRunnerBundle;
import com.intellij.execution.ui.ConsoleViewContentType;
import org.jetbrains.annotations.NonNls;

/**
 * @author Roman Chernyatchik
 */
public abstract class SuiteFinishedState extends AbstractState {
  @NonNls private static final String EMPTY_SUITE_TEXT = SMTestsRunnerBundle.message("sm.test.runner.states.suite.is.empty");

  //This states are common for all instances and doesn't contains
  //instance-specific information

  public static SuiteFinishedState PASSED_SUITE = new SuiteFinishedState() {
    public Magnitude getMagnitude() {
      return Magnitude.PASSED_INDEX;
    }

    @Override
    public String toString() {
      //noinspection HardCodedStringLiteral
      return "SUITE PASSED";
    }
  };
  public static SuiteFinishedState FAILED_SUITE = new SuiteFinishedState() {
    @Override
    public boolean isDefect() {
      return true;
    }

    public Magnitude getMagnitude() {
      return Magnitude.FAILED_INDEX;
    }

    @Override
    public String toString() {
      //noinspection HardCodedStringLiteral
      return "FAILED SUITE";
    }
  };

  public static SuiteFinishedState WITH_IGNORED_TESTS_SUITE = new SuiteFinishedState() {
    @Override
    public boolean isDefect() {
      return true;
    }

    public Magnitude getMagnitude() {
      return Magnitude.IGNORED_INDEX;
    }

    @Override
    public String toString() {
      //noinspection HardCodedStringLiteral
      return "WITH IGNORED TESTS SUITE";
    }
  };

  public static SuiteFinishedState ERROR_SUITE = new SuiteFinishedState() {
    @Override
    public boolean isDefect() {
      return true;
    }

    public Magnitude getMagnitude() {
      return Magnitude.ERROR_INDEX;
    }

    @Override
    public String toString() {
      //noinspection HardCodedStringLiteral
      return "ERROR SUITE";
    }
  };

  /**
   * Finished empty leaf test suite
   */
  public static SuiteFinishedState EMPTY_LEAF_SUITE = new EmptySuite() {

    @Override
    public void printOn(final Printer printer) {
      super.printOn(printer);

      final String msg = EMPTY_SUITE_TEXT + CompositePrintable.NEW_LINE;
      printer.print(msg, ConsoleViewContentType.SYSTEM_OUTPUT);
    }

  };

  /**
   * Finished empty test suite
   */
  public static SuiteFinishedState EMPTY_SUITE = new EmptySuite();

  /**
   * Finished byt tests reporter wasn't attached
   */
  public static SuiteFinishedState TESTS_REPORTER_NOT_ATTACHED = new SuiteFinishedState() {
    @Override
    public boolean isDefect() {
      return false;
    }

    public Magnitude getMagnitude() {
      return Magnitude.COMPLETE_INDEX;
    }

    @Override
    public String toString() {
      //noinspection HardCodedStringLiteral
      return "TESTS REPORTER NOT ATTACHED";
    }
  };

  private SuiteFinishedState() {
  }

  public boolean isInProgress() {
    return false;
  }

  public boolean isDefect() {
    return false;
  }

  public boolean wasLaunched() {
    return true;
  }

  public boolean isFinal() {
    return true;
  }

  public boolean wasTerminated() {
    return false;
  }

  private static class EmptySuite extends SuiteFinishedState {

    @Override
    public boolean isDefect() {
      return false;
    }

    public Magnitude getMagnitude() {
      return Magnitude.COMPLETE_INDEX;
    }

    @Override
    public String toString() {
      //noinspection HardCodedStringLiteral
      return "EMPTY FINISHED SUITE";
    }
  }

}
