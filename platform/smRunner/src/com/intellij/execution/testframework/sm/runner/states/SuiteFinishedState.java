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

/**
 * @author Roman Chernyatchik
 */
public abstract class SuiteFinishedState extends AbstractState {
  //This states are common for all instances and doesn't contains
  //instance-specific information

  public static SuiteFinishedState PASSED_SUITE = new SuiteFinishedState() {
    @Override
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

    @Override
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

    @Override
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

    @Override
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
   * Finished empty test suite
   */
  public static SuiteFinishedState EMPTY_SUITE = new EmptySuite();

  /**
   * Finished byt tests reporter wasn't attached
   */
  public static SuiteFinishedState TESTS_REPORTER_NOT_ATTACHED = new SuiteFinishedState() {
    @Override
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

  @Override
  public boolean isInProgress() {
    return false;
  }

  @Override
  public boolean isDefect() {
    return false;
  }

  @Override
  public boolean wasLaunched() {
    return true;
  }

  @Override
  public boolean isFinal() {
    return true;
  }

  @Override
  public boolean wasTerminated() {
    return false;
  }

  private static class EmptySuite extends SuiteFinishedState {
    @Override
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
