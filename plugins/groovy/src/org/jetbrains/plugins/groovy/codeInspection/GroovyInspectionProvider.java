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
package org.jetbrains.plugins.groovy.codeInspection;

import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.annotator.inspections.GroovyImmutableAnnotationInspection;
import org.jetbrains.plugins.groovy.annotator.inspections.GroovySingletonAnnotationInspection;
import org.jetbrains.plugins.groovy.codeInspection.assignment.*;
import org.jetbrains.plugins.groovy.codeInspection.bugs.*;
import org.jetbrains.plugins.groovy.codeInspection.confusing.*;
import org.jetbrains.plugins.groovy.codeInspection.control.*;
import org.jetbrains.plugins.groovy.codeInspection.exception.*;
import org.jetbrains.plugins.groovy.codeInspection.gpath.*;
import org.jetbrains.plugins.groovy.codeInspection.metrics.*;
import org.jetbrains.plugins.groovy.codeInspection.naming.*;
import org.jetbrains.plugins.groovy.codeInspection.noReturnMethod.MissingReturnInspection;
import org.jetbrains.plugins.groovy.codeInspection.secondUnsafeCall.SecondUnsafeCallInspection;
import org.jetbrains.plugins.groovy.codeInspection.threading.*;
import org.jetbrains.plugins.groovy.codeInspection.unassignedVariable.UnassignedVariableAccessInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GroovyUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GroovyUntypedAccessInspection;
import org.jetbrains.plugins.groovy.codeInspection.unusedDef.UnusedDefInspection;
import org.jetbrains.plugins.groovy.codeInspection.validity.GroovyDuplicateSwitchBranchInspection;
import org.jetbrains.plugins.groovy.codeInspection.validity.GroovyUnreachableStatementInspection;

/**
 * @author ilyas
 */
public class GroovyInspectionProvider implements InspectionToolProvider, ApplicationComponent {

  public Class[] getInspectionClasses() {
    return new Class[] {
        SecondUnsafeCallInspection.class,
        UnusedDefInspection.class,
        UnassignedVariableAccessInspection.class,
        MissingReturnInspection.class,

        GroovyAssignabilityCheckInspection.class,
        GroovyResultOfAssignmentUsedInspection.class,
        GroovyAssignmentCanBeOperatorAssignmentInspection.class,
        GroovyAssignmentToForLoopParameterInspection.class,
        GroovyAssignmentToMethodParameterInspection.class,
        GroovyNestedAssignmentInspection.class,
        GroovySillyAssignmentInspection.class,
        GroovyUncheckedAssignmentOfMemberOfRawTypeInspection.class,

        GroovyContinueOrBreakFromFinallyBlockInspection.class,
        GroovyReturnFromFinallyBlockInspection.class,
        GroovyThrowFromFinallyBlockInspection.class,
        GroovyEmptyCatchBlockInspection.class,
        GroovyEmptyFinallyBlockInspection.class,
        GroovyEmptyTryBlockInspection.class,
        GroovyUnusedCatchParameterInspection.class,

        GroovyBreakInspection.class,
        GroovyContinueInspection.class,
        GroovyUnreachableStatementInspection.class,
        GroovyLoopStatementThatDoesntLoopInspection.class,
        GroovyConditionalWithIdenticalBranchesInspection.class,
        GroovyConditionalCanBeElvisInspection.class,
        GroovyConditionalCanBeConditionalCallInspection.class,
        GroovyIfStatementWithIdenticalBranchesInspection.class,
        GroovyIfStatementWithTooManyBranchesInspection.class,
        GroovyFallthroughInspection.class,
        GroovyUnnecessaryContinueInspection.class,
        GroovyUnnecessaryReturnInspection.class,
        GroovySwitchStatementWithNoDefaultInspection.class,
        GroovyReturnFromClosureCanBeImplicitInspection.class,
        GroovyTrivialConditionalInspection.class,
        GroovyConstantConditionalInspection.class,
        GroovyConstantIfStatementInspection.class,
        GroovyTrivialIfInspection.class,

        GroovyAccessToStaticFieldLockedOnInstanceInspection.class,
        GroovyDoubleCheckedLockingInspection.class,
        GroovyUnconditionalWaitInspection.class,
        GroovyPublicFieldAccessedInSynchronizedContextInspection.class,
        GroovyBusyWaitInspection.class,
        GroovyEmptySyncBlockInspection.class,
        GroovySynchronizationOnThisInspection.class,
        GroovySynchronizedMethodInspection.class,
        GroovyNestedSynchronizedStatementInspection.class,
        GroovyThreadStopSuspendResumeInspection.class,
        GroovySystemRunFinalizersOnExitInspection.class,
        GroovyNotifyWhileNotSynchronizedInspection.class,
        GroovyWaitCallNotInLoopInspection.class,
        GroovyWaitWhileNotSynchronizedInspection.class,
        GroovySynchronizationOnNonFinalFieldInspection.class,
        GroovySynchronizationOnVariableInitializedWithLiteralInspection.class,
        GroovyUnsynchronizedMethodOverridesSynchronizedMethodInspection.class,
        GroovyWhileLoopSpinsOnFieldInspection.class,

        GroovyMethodParameterCountInspection.class,
        GroovyOverlyComplexMethodInspection.class,
        GroovyOverlyLongMethodInspection.class,
        GroovyOverlyNestedMethodInspection.class,
        GroovyMethodWithMoreThanThreeNegationsInspection.class,
        GroovyMultipleReturnPointsPerMethodInspection.class,

        GroovyNestedSwitchInspection.class,
        GroovyConditionalInspection.class,
        GroovyNestedConditionalInspection.class,
        GroovyNegatedConditionalInspection.class,
        GroovyNegatedIfInspection.class,
        GroovyResultOfIncrementOrDecrementUsedInspection.class,
        GroovyEmptyStatementBodyInspection.class,
        GroovyPointlessBooleanInspection.class,
        GroovyPointlessArithmeticInspection.class,
        GroovyDoubleNegationInspection.class,
        GroovyOverlyComplexArithmeticExpressionInspection.class,
        GroovyOverlyComplexBooleanExpressionInspection.class,
        GroovyOctalIntegerInspection.class,

        GroovyDuplicateSwitchBranchInspection.class,

        GroovyNonShortCircuitBooleanInspection.class,
        GroovyInfiniteLoopStatementInspection.class,
        GroovyInfiniteRecursionInspection.class,
        GroovyDivideByZeroInspection.class,
        GroovyResultOfObjectAllocationIgnoredInspection.class,

        GroovyClassNamingConventionInspection.class,
        GroovyInterfaceNamingConventionInspection.class,
        GroovyAnnotationNamingConventionInspection.class,
        GroovyEnumerationNamingConventionInspection.class,
        GroovyLocalVariableNamingConventionInspection.class,
        GroovyStaticMethodNamingConventionInspection.class,
        GroovyStaticVariableNamingConventionInspection.class,
        GroovyInstanceMethodNamingConventionInspection.class,
        GroovyInstanceVariableNamingConventionInspection.class,
        GroovyConstantNamingConventionInspection.class,
        GroovyParameterNamingConventionInspection.class,

        GroovyGetterCallCanBePropertyAccessInspection.class,
        GroovySetterCallCanBePropertyAccessInspection.class,
        GroovyMapGetCanBeKeyedAccessInspection.class,
        GroovyMapPutCanBeKeyedAccessInspection.class,
        GroovyListGetCanBeKeyedAccessInspection.class,
        GroovyListSetCanBeKeyedAccessInspection.class,

        GroovyUntypedAccessInspection.class,
        GroovyUnresolvedAccessInspection.class,

        GroovyImmutableAnnotationInspection.class,
        GroovySingletonAnnotationInspection.class      
    };
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "GroovyInspectionProvider";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }
}
