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
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.Location;
import com.intellij.execution.testframework.*;
import com.intellij.execution.testframework.sm.SMStacktraceParser;
import com.intellij.execution.testframework.sm.TestsLocationProviderUtil;
import com.intellij.execution.testframework.sm.runner.states.*;
import com.intellij.execution.testframework.sm.runner.ui.TestsPresentationUtil;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testIntegration.TestLocationProvider;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author: Roman Chernyatchik
 */
public class SMTestProxy extends AbstractTestProxy {
  private static final Logger LOG = Logger.getInstance(SMTestProxy.class.getName());

  private List<SMTestProxy> myChildren;
  private SMTestProxy myParent;

  private AbstractState myState = NotRunState.getInstance();
  private final String myName;
  private Long myDuration = null; // duration is unknown
  @Nullable private final String myLocationUrl;
  private boolean myDurationIsCached = false; // is used for separating unknown and unset duration
  private boolean myHasCriticalErrors = false;
  private boolean myHasErrorsCached = false;
  private boolean myHasPassedTests = false;
  private boolean myHasPassedTestsCached = false;

  @Nullable private String myStacktrace;

  private final boolean myIsSuite;
  private boolean myIsEmptyIsCached = false; // is used for separating unknown and unset values
  private boolean myIsEmpty = true;
  TestLocationProvider myLocator = null;
  private final boolean myPreservePresentableName;
  private Printer myPreferredPrinter = null;

  public SMTestProxy(final String testName, final boolean isSuite,
                     @Nullable final String locationUrl) {
    this(testName, isSuite, locationUrl, false);
  }

  public SMTestProxy(final String testName, final boolean isSuite,
                     @Nullable final String locationUrl,
                     boolean preservePresentableName) {
    myName = testName;
    myIsSuite = isSuite;
    myLocationUrl = locationUrl;
    myPreservePresentableName = preservePresentableName;
  }

  public void setLocator(@NotNull TestLocationProvider locator) {
    myLocator = locator;
  }

  public void setPreferredPrinter(@NotNull Printer preferredPrinter) {
    myPreferredPrinter = preferredPrinter;
  }

  public boolean isInProgress() {
    //final SMTestProxy parent = getParent();

    return myState.isInProgress();
  }

  public boolean isDefect() {
    return myState.isDefect();
  }

  public boolean shouldRun() {
    return true;
  }

  public int getMagnitude() {
    // Is used by some of Tests Filters

    //WARN: It is Hack, see PoolOfTestStates, API is necessary
    return getMagnitudeInfo().getValue();
  }

  public TestStateInfo.Magnitude getMagnitudeInfo() {
    return myState.getMagnitude();
  }

  public boolean hasErrors() {
    // if already cached
    if (myHasErrorsCached) {
      return myHasCriticalErrors;
    }

    final boolean canCacheErrors = !myState.isInProgress();
    // calculate
    final boolean hasErrors = calcHasErrors();
    if (canCacheErrors) {
      myHasCriticalErrors = hasErrors;
      myHasErrorsCached = true;
    }
    return hasErrors;
  }

  private boolean calcHasErrors() {
    if (myHasCriticalErrors) {
      return true;
    }

    for (SMTestProxy child : getChildren()) {
      if (child.hasErrors()) {
        return true;
      }
    }
    return false;
  }

  private void setStacktraceIfNotSet(@Nullable String stacktrace) {
    if (myStacktrace == null) myStacktrace = stacktrace;
  }

  public boolean isLeaf() {
    return myChildren == null || myChildren.isEmpty();
  }

  @Override
  public boolean isInterrupted() {
    return myState.wasTerminated();
  }

  boolean hasPassedTests() {
    if (myHasPassedTestsCached) {
      return myHasPassedTests;
    }
    boolean hasPassedTests = calcPassedTests();
    boolean canCache = !myState.isInProgress();
    if (canCache) {
      myHasPassedTests = hasPassedTests;
      myHasPassedTestsCached = true;
    }
    return hasPassedTests;
  }

  private boolean calcPassedTests() {
    if (isPassed()) {
      return true;
    }
    for (SMTestProxy child : getChildren()) {
      if (child.hasPassedTests()) {
        return true;
      }
    }
    return false;
  }

    @Override
  public boolean isIgnored() {
    if (hasPassedTests()) {
      return false;
    }
    return myState.getMagnitude() == TestStateInfo.Magnitude.IGNORED_INDEX;
  }

  public boolean isPassed() {
    return myState.getMagnitude() == TestStateInfo.Magnitude.SKIPPED_INDEX ||
           myState.getMagnitude() == TestStateInfo.Magnitude.COMPLETE_INDEX ||
           myState.getMagnitude() == TestStateInfo.Magnitude.PASSED_INDEX; 
  }

  public void addChild(final SMTestProxy child) {
    if (myChildren == null) {
      myChildren = new ArrayList<SMTestProxy>();
    }
    myChildren.add(child);

    // add printable
    //
    // add link to child's future output in correct place
    // actually if after this suite will obtain output
    // it will place it after this child and before future child
    addLast(child);

    // add child
    //
    //TODO reset children cache
    child.setParent(this);
    // if parent is being printed then all childs output
    // should be also send to the same printer
    child.setPrinter(myPrinter);
    if (myPreferredPrinter != null && child.myPreferredPrinter == null) {
      child.setPreferredPrinter(myPreferredPrinter);
    }
  }

  @Nullable
  private Printer getRightPrinter(@Nullable Printer printer) {
    if (myPreferredPrinter != null && printer != null) {
      return myPreferredPrinter;
    }
    return printer;
  }

  public void setPrinter(Printer printer) {
    super.setPrinter(getRightPrinter(printer));
  }


  public String getName() {
    return myName;
  }

  @Nullable
  public Location getLocation(final Project project, GlobalSearchScope searchScope) {
    //determines location of test proxy

    //TODO multiresolve support

    if (myLocationUrl == null || myLocator == null) {
      return null;
    }

    final String protocolId = VirtualFileManager.extractProtocol(myLocationUrl);
    final String path = TestsLocationProviderUtil.extractPath(myLocationUrl);

    if (protocolId != null && path != null) {
      List<Location> locations = myLocator.getLocation(protocolId, path, project);
      if (!locations.isEmpty()) {
        return locations.iterator().next();
      }
    }

    return null;
  }

  @Nullable
  public Navigatable getDescriptor(final Location location, final TestConsoleProperties testConsoleProperties) {
    // by location gets navigatable element.
    // It can be file or place in file (e.g. when OPEN_FAILURE_LINE is enabled)
    if (location == null) return null;

    final String stacktrace = myStacktrace;
    if (stacktrace != null && (testConsoleProperties instanceof SMStacktraceParser) && isLeaf()) {
      final Navigatable result = ((SMStacktraceParser)testConsoleProperties).getErrorNavigatable(location.getProject(), stacktrace);
      if (result != null) {
        return result;
      }
    }

    return EditSourceUtil.getDescriptor(location.getPsiElement());
  }

  public boolean isSuite() {
    return myIsSuite;
  }

  public SMTestProxy getParent() {
    return myParent;
  }

  public List<? extends SMTestProxy> getChildren() {
    return myChildren != null ? myChildren : Collections.<SMTestProxy>emptyList();
  }

  public List<SMTestProxy> getAllTests() {
    final List<SMTestProxy> allTests = new ArrayList<SMTestProxy>();

    allTests.add(this);

    for (SMTestProxy child : getChildren()) {
      allTests.addAll(child.getAllTests());
    }

    return allTests;
  }


  public void setStarted() {
    myState = !myIsSuite ? TestInProgressState.TEST : new SuiteInProgressState(this);
  }

  /**
   * Calculates and caches duration of test or suite
   * @return null if duration is unknown, otherwise duration value in milliseconds;
   */
  @Nullable
  @Override
  public Long getDuration() {
    // Returns duration value for tests
    // or cached duration for suites
    if (myDurationIsCached || !isSuite()) {
      return myDuration;
    }

    //For suites counts and caches durations of its children. Also it evaluates partial duration,
    //i.e. if duration is unknown it will be ignored in summary value.
    //If duration for all children is unknown summary duration will be also unknown
    //if one of children is ignored - it's duration will be 0 and if child wasn't run,
    //then it's duration will be unknown
    myDuration = calcSuiteDuration();
    myDurationIsCached = true;

    return myDuration;
  }

  @Override
  public boolean shouldSkipRootNodeForExport() {
    return true;
  }

  /**
   * Sets duration of test
   * @param duration In milliseconds
   */
  public void setDuration(final long duration) {
    invalidateCachedDurationForContainerSuites();

    if (!isSuite()) {
      myDurationIsCached = true;
      myDuration = (duration >= 0) ? duration : null;
      return;
    }

    // Not allow to directly set duration for suites.
    // It should be the sum of children. This requirement is only
    // for safety of current model and may be changed
    LOG.warn("Unsupported operation");
  }

  public void setFinished() {
    if (myState.isFinal()) {
      // we shouldn't fire new printable because final state
      // has been already fired
      return;
    }

    if (!isSuite()) {
      // if isn't in other finished state (ignored, failed or passed)
      myState = TestPassedState.INSTANCE;
    } else {
      //Test Suite
      myState = determineSuiteStateOnFinished();
    }
    // prints final state additional info
    fireOnNewPrintable(myState);
  }

  public void setTestFailed(@NotNull final String localizedMessage,
                            @Nullable final String stackTrace,
                            final boolean testError) {
    setStacktraceIfNotSet(stackTrace);
    if (myState instanceof TestFailedState) {
      ((TestFailedState) myState).addError(localizedMessage, stackTrace, myPrinter);
    }
    else {
      myState = testError
                ? new TestErrorState(localizedMessage, stackTrace)
                : new TestFailedState(localizedMessage, stackTrace);
      fireOnNewPrintable(myState);
    }
  }

  public void setTestComparisonFailed(@NotNull final String localizedMessage,
                                      @Nullable final String stackTrace,
                                      @NotNull final String actualText,
                                      @NotNull final String expectedText) {
    setStacktraceIfNotSet(stackTrace);
    myState = new TestComparisionFailedState(localizedMessage, stackTrace,
                                             actualText, expectedText);
    fireOnNewPrintable(myState);
  }

  public void setTestIgnored(@NotNull final String ignoreComment,
                             @Nullable final String stackTrace) {
    setStacktraceIfNotSet(stackTrace);
    myState = new TestIgnoredState(ignoreComment, stackTrace);
    fireOnNewPrintable(myState);
  }

  public void setParent(@Nullable final SMTestProxy parent) {
    myParent = parent;
  }

  public List<? extends SMTestProxy> collectChildren(@Nullable final Filter<SMTestProxy> filter) {
    return filterChildren(filter, collectChildren());
  }

  public List<? extends SMTestProxy> collectChildren() {
    final List<? extends SMTestProxy> allChildren = getChildren();

    final List<SMTestProxy> result = ContainerUtilRt.newArrayList();

    result.addAll(allChildren);

    for (SMTestProxy p: allChildren) {
      result.addAll(p.collectChildren());
    }

    return result;
  }

  public List<? extends SMTestProxy> getChildren(@Nullable final Filter<? super SMTestProxy> filter) {
    final List<? extends SMTestProxy> allChildren = getChildren();

    return filterChildren(filter, allChildren);
  }

  private static List<? extends SMTestProxy> filterChildren(@Nullable Filter<? super SMTestProxy> filter,
                                                            List<? extends SMTestProxy> allChildren) {
    if (filter == Filter.NO_FILTER || filter == null) {
      return allChildren;
    }

    final List<SMTestProxy> selectedChildren = new ArrayList<SMTestProxy>();
    for (SMTestProxy child : allChildren) {
      if (filter.shouldAccept(child)) {
        selectedChildren.add(child);
      }
    }

    if ((selectedChildren.isEmpty())) {
      return Collections.<SMTestProxy>emptyList();
    }
    return selectedChildren;
  }

  public boolean wasLaunched() {
    return myState.wasLaunched();
  }


  /**
   * Prints this proxy and all its children on given printer
   * @param printer Printer
   */
  public void printOn(final Printer printer) {
    final Printer rightPrinter = getRightPrinter(printer);
    super.printOn(rightPrinter);
    final AbstractState oldState = myState;

    invokeInAlarm(new Runnable() {
      @Override
      public void run() {
        //Tests State, that provide and formats additional output
        oldState.printOn(rightPrinter);
      }
    });
  }

  public void addStdOutput(final String output, final Key outputType) {
    addLast(new Printable() {
      public void printOn(final Printer printer) {
        printer.print(output, ConsoleViewContentType.getConsoleViewType(outputType));
      }
    });
  }

  public void addStdErr(final String output) {
    addLast(new Printable() {
      public void printOn(final Printer printer) {
        printer.print(output, ConsoleViewContentType.ERROR_OUTPUT);
      }
    });
  }

  /**
   * This method was left for backward compatibility.
   *
   * @param output
   * @param stackTrace
   * @deprecated use SMTestProxy.addError(String output, String stackTrace, boolean isCritical)
   */
  @Deprecated
  public void addError(final String output,
                       @Nullable final String stackTrace) {
    addError(output, stackTrace, true);
  }

  public void addError(final String output,
                       @Nullable final String stackTrace,
                       final boolean isCritical) {
    myHasCriticalErrors = isCritical;
    setStacktraceIfNotSet(stackTrace);

    addLast(new Printable() {
      public void printOn(final Printer printer) {
        final String errorText = TestFailedState.buildErrorPresentationText(output, stackTrace);
        LOG.assertTrue(errorText != null);

        TestFailedState.printError(printer, Arrays.asList(errorText));
      }
    });
  }

  public void addSystemOutput(final String output) {
    addLast(new Printable() {
      public void printOn(final Printer printer) {
        printer.print(output, ConsoleViewContentType.SYSTEM_OUTPUT);
      }
    });
  }

  @NotNull
  public String getPresentableName() {
    if (myPreservePresentableName) {
      return TestsPresentationUtil.getPresentableNameTrimmedOnly(this);
    }
    return TestsPresentationUtil.getPresentableName(this);
  }

  @Override
  @Nullable
  public AssertEqualsDiffViewerProvider getDiffViewerProvider() {
    if (myState instanceof AssertEqualsDiffViewerProvider) {
      return (AssertEqualsDiffViewerProvider)myState;
    }
    return null;
  }

  @Override
  public String toString() {
    return getPresentableName();
  }

  /**
   * Process was terminated
   */
  public void setTerminated() {
    if (myState.isFinal()) {
      return;
    }
    myState = TerminatedState.INSTANCE;
    final List<? extends SMTestProxy> children = getChildren();
    for (SMTestProxy child : children) {
      child.setTerminated();
    }
    fireOnNewPrintable(myState);
  }

  public boolean wasTerminated() {
    return myState.wasTerminated();
  }

  @Nullable
  protected String getLocationUrl() {
    return myLocationUrl;
  }

  /**
   * Check if suite contains error tests or suites
   * @return True if contains
   */
  private boolean containsErrorTests() {
    final List<? extends SMTestProxy> children = getChildren();
    for (SMTestProxy child : children) {
      if (child.getMagnitudeInfo() == TestStateInfo.Magnitude.ERROR_INDEX) {
        return true;
      }
    }
    return false;
  }

  private boolean containsFailedTests() {
    final List<? extends SMTestProxy> children = getChildren();
    for (SMTestProxy child : children) {
      if (child.getMagnitudeInfo() == TestStateInfo.Magnitude.FAILED_INDEX) {
        return true;
      }
    }
    return false;
  }

  /**
   * Determines site state after it has been finished
   * @return New state
   */
  protected AbstractState determineSuiteStateOnFinished() {
    final AbstractState state;
    if (isLeaf()) {
      state = SuiteFinishedState.EMPTY_LEAF_SUITE;
    } else if (isEmptySuite()) {
      state = SuiteFinishedState.EMPTY_SUITE;
    } else {
      if (isDefect()) {
        // Test suit contains errors if at least one of its tests contains error
        if (containsErrorTests()) {
          state = SuiteFinishedState.ERROR_SUITE;
        } else {
          // if suite contains failed tests - all suite should be
          // consider as failed
          state = containsFailedTests()
                   ? SuiteFinishedState.FAILED_SUITE
                   : SuiteFinishedState.WITH_IGNORED_TESTS_SUITE;
        }
      } else {
        state = SuiteFinishedState.PASSED_SUITE;
      }
    }
    return state;
  }

  public boolean isEmptySuite() {
    if (myIsEmptyIsCached) {
      return myIsEmpty;
    }

    if (!isSuite()) {
      // test - no matter what we will return
      myIsEmpty = true;
      myIsEmptyIsCached = true;

      return myIsEmpty;
    }

    myIsEmpty = true;
    final List<? extends SMTestProxy> allTestCases = getChildren();
    for (SMTestProxy testOrSuite : allTestCases) {
      if (testOrSuite.isSuite()) {
        // suite
        if (!testOrSuite.isEmptySuite()) {
          // => parent suite isn't empty
          myIsEmpty = false;
          myIsEmptyIsCached = true;
          break;
        }
        // all suites are empty
        myIsEmpty = true;
        // we can cache only final state, otherwise test may be added
        myIsEmptyIsCached = myState.isFinal();
      } else {
        // test => parent suite isn't empty
        myIsEmpty = false;
        myIsEmptyIsCached = true;
        break;
      }
    }
    return myIsEmpty;
  }



  @Nullable
  private Long calcSuiteDuration() {
    long partialDuration = 0;
    boolean durationOfChildrenIsUnknown = true;

    for (SMTestProxy child : getChildren()) {
      final Long duration = child.getDuration();
      if (duration != null) {
        durationOfChildrenIsUnknown = false;
        partialDuration += duration.longValue();
      }
    }
    // Lets convert partial duration in integer object. Negative partial duration
    // means that duration of all children is unknown
    return durationOfChildrenIsUnknown ? null : partialDuration;
  }

  /**
   * Recursively invalidates cached duration for container(parent) suites
   */
  private void invalidateCachedDurationForContainerSuites() {
    // Invalidates duration of this suite
    myDuration = null;
    myDurationIsCached = false;

    // Invalidates duration of container suite
    final SMTestProxy containerSuite = getParent();
    if (containerSuite != null) {
      containerSuite.invalidateCachedDurationForContainerSuites();
    }
  }

  public static class SMRootTestProxy extends SMTestProxy {
    private boolean myTestsReporterAttached; // false by default

    public SMRootTestProxy() {
      super("[root]", true, null);
    }

    public void setTestsReporterAttached() {
      myTestsReporterAttached = true;
    }

    public boolean isTestsReporterAttached() {
      return myTestsReporterAttached;
    }

    @Override
    protected AbstractState determineSuiteStateOnFinished() {
      if (isLeaf() && !isTestsReporterAttached()) {
        return SuiteFinishedState.TESTS_REPORTER_NOT_ATTACHED;
      }
      return super.determineSuiteStateOnFinished();
    }
  }
}
