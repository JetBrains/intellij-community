/*
* Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.booleanIsAlwaysInverted.BooleanMethodIsAlwaysInvertedInspection;
import com.intellij.openapi.components.ApplicationComponent;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.abstraction.*;
import com.siyeh.ig.assignment.*;
import com.siyeh.ig.bitwise.IncompatibleMaskInspection;
import com.siyeh.ig.bitwise.PointlessBitwiseExpressionInspection;
import com.siyeh.ig.bitwise.ShiftOutOfRangeInspection;
import com.siyeh.ig.bugs.*;
import com.siyeh.ig.classlayout.*;
import com.siyeh.ig.classmetrics.*;
import com.siyeh.ig.cloneable.*;
import com.siyeh.ig.controlflow.*;
import com.siyeh.ig.dataflow.ReuseOfLocalVariableInspection;
import com.siyeh.ig.dataflow.TooBroadScopeInspection;
import com.siyeh.ig.dataflow.UnnecessaryLocalVariableInspection;
import com.siyeh.ig.encapsulation.*;
import com.siyeh.ig.errorhandling.*;
import com.siyeh.ig.finalization.FinalizeCallsSuperFinalizeInspection;
import com.siyeh.ig.finalization.FinalizeInspection;
import com.siyeh.ig.finalization.FinalizeNotProtectedInspection;
import com.siyeh.ig.finalization.NoExplicitFinalizeCallsInspection;
import com.siyeh.ig.imports.*;
import com.siyeh.ig.initialization.*;
import com.siyeh.ig.internationalization.*;
import com.siyeh.ig.j2me.*;
import com.siyeh.ig.jdk.*;
import com.siyeh.ig.jdk15.*;
import com.siyeh.ig.junit.*;
import com.siyeh.ig.logging.ClassWithMultipleLoggersInspection;
import com.siyeh.ig.logging.ClassWithoutLoggerInspection;
import com.siyeh.ig.logging.NonStaticFinalLoggerInspection;
import com.siyeh.ig.maturity.*;
import com.siyeh.ig.memory.StaticCollectionInspection;
import com.siyeh.ig.memory.StringBufferFieldInspection;
import com.siyeh.ig.memory.SystemGCInspection;
import com.siyeh.ig.memory.ZeroLengthArrayInitializationInspection;
import com.siyeh.ig.methodmetrics.*;
import com.siyeh.ig.naming.*;
import com.siyeh.ig.numeric.*;
import com.siyeh.ig.performance.*;
import com.siyeh.ig.portability.*;
import com.siyeh.ig.resources.*;
import com.siyeh.ig.security.*;
import com.siyeh.ig.serialization.*;
import com.siyeh.ig.style.*;
import com.siyeh.ig.telemetry.InspectionGadgetsTelemetry;
import com.siyeh.ig.threading.*;
import com.siyeh.ig.visibility.*;
import com.siyeh.ig.packaging.*;
import com.siyeh.ig.dependency.*;
import com.siyeh.ig.modularization.ModuleWithTooFewClassesInspection;
import com.siyeh.ig.modularization.ModuleWithTooManyClassesInspection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;

@SuppressWarnings({"OverlyCoupledClass",
        "OverlyCoupledMethod",
        "OverlyLongMethod",
        "ClassWithTooManyMethods"})
public class InspectionGadgetsPlugin implements ApplicationComponent,
        InspectionToolProvider {

    private static final int NUM_INSPECTIONS = 540;
    private final List<Class<? extends InspectionProfileEntry>> m_inspectionClasses =
            new ArrayList<Class<? extends InspectionProfileEntry>>(NUM_INSPECTIONS);
    @NonNls private static final String DESCRIPTION_DIRECTORY_NAME =
            "src/inspectionDescriptions/";
    private final InspectionGadgetsTelemetry telemetry =
            new InspectionGadgetsTelemetry();
    private static final boolean TELEMETRY_ENABLED = false;
    @NonNls private static final String INSPECTION = "Inspection";
    @NonNls private static final String BUILD_FIXES_ONLY_ON_THE_FLY = "(r)";

    public static void main(String[] args) {
        final PrintStream out;
        if (args.length == 0) {
            out = System.out;
        } else {
            final OutputStream stream;
            try {
                stream = new FileOutputStream(args[0]);
            } catch (final FileNotFoundException e) {
                System.err.println(e.getMessage());
                return;
            }
            out = new PrintStream(stream);
        }
        final InspectionGadgetsPlugin plugin = new InspectionGadgetsPlugin();
        plugin.createDocumentation(out);
    }

    private void createDocumentation(PrintStream out) {
        final Class<? extends InspectionProfileEntry>[] classes =
                getInspectionClasses();
        Arrays.sort(classes, new InspectionComparator());

        final int numQuickFixes = countQuickFixes(classes, out);
        out.println(InspectionGadgetsBundle.message(
                "create.documentation.count.inspections.message",
                Integer.valueOf(classes.length)));
        out.println(InspectionGadgetsBundle.message(
                "create.documentation.count.quick.fixes.message",
                Integer.valueOf(numQuickFixes)));
        String currentGroupName = "";

        for (final Class<? extends InspectionProfileEntry> aClass : classes) {
            final String className = aClass.getName();
            try {
                final InspectionProfileEntry inspection =
                        aClass.newInstance();
                final String groupDisplayName =
                        inspection.getGroupDisplayName();
                if (!groupDisplayName.equals(currentGroupName)) {
                    currentGroupName = groupDisplayName;
                    out.println();
                    out.print("   * ");
                    out.println(currentGroupName);
                }
                printInspectionDescription(inspection, out);
            } catch (InstantiationException ignore) {
                out.println(InspectionGadgetsBundle.message(
                        "create.documentation.couldn.t.instantiate.class",
                        className));
            } catch (IllegalAccessException ignore) {
                out.println(InspectionGadgetsBundle.message(
                        "create.documentation.couldnt.access.class", className));
            } catch (ClassCastException ignore) {
                out.println(InspectionGadgetsBundle.message(
                        "create.documentation.couldnt.cast.class", className));
            }
        }

        out.println();
        out.println(InspectionGadgetsBundle.message(
                "create.documentation.inspections.enabled.by.default.message"));
        for (final Class<? extends InspectionProfileEntry> aClass : classes) {
            final String className = aClass.getName();
            try {
                final InspectionProfileEntry inspection =
                        aClass.newInstance();
                if (inspection.isEnabledByDefault()) {
                    out.println('\t' + inspection.getDisplayName());
                }
            } catch (InstantiationException ignore) {
                out.println(InspectionGadgetsBundle.message(
                        "create.documentation.couldn.t.instantiate.class",
                        className));
            } catch (IllegalAccessException ignore) {
                out.println(InspectionGadgetsBundle.message(
                        "create.documentation.couldnt.access.class", className));
            } catch (ClassCastException ignore) {
                out.println(InspectionGadgetsBundle.message(
                        "create.documentation.couldnt.cast.class", className));
            }
        }
        final File descriptionDirectory = new File(DESCRIPTION_DIRECTORY_NAME);
        final File[] descriptionFiles = descriptionDirectory.listFiles();
        final Set<File> descriptionFilesSet = new HashSet<File>(
                descriptionFiles.length);
        for (File descriptionFile : descriptionFiles) {
            final String name = descriptionFile.getName();
            if (!(name.length() > 0 && name.charAt(0) == '.')) {
                descriptionFilesSet.add(descriptionFile);
            }
        }
        for (final Class<? extends InspectionProfileEntry> aClass : classes) {
            final String className = aClass.getName();
            final String simpleClassName =
                    className.substring(className.lastIndexOf('.') + 1,
                            className.length() -
                                    INSPECTION.length());
            @NonNls final String fileName =
                    DESCRIPTION_DIRECTORY_NAME + simpleClassName + ".html";
            final File descriptionFile = new File(fileName);
            if (descriptionFile.exists()) {
                descriptionFilesSet.remove(descriptionFile);
            } else {
                out.println(InspectionGadgetsBundle.message(
                        "create.documentation.couldnt.find.documentation.file.error.message",
                        fileName));
            }
        }
        for (final File file : descriptionFilesSet) {
            out.println(
                    InspectionGadgetsBundle.message(
                            "create.documentation.unused.documentation.file.error.message",
                            file.getAbsolutePath()));
        }
    }

    private static void printInspectionDescription(InspectionProfileEntry inspection,
                                                   PrintStream out) {
        boolean hasQuickFix = false;
        BaseInspection baseInspection = null;
        if (!(inspection instanceof GlobalInspectionTool)) {
            baseInspection = (BaseInspection) inspection;
            hasQuickFix = baseInspection.hasQuickFix();
        }
        final String displayName = inspection.getDisplayName();
        out.print("      * ");
        out.print(displayName);
        if (hasQuickFix) {
            if (baseInspection.buildQuickFixesOnlyForOnTheFlyErrors()) {
                out.print(BUILD_FIXES_ONLY_ON_THE_FLY);
            } else {
                out.print("(*)");
            }
        }
        out.println();
    }

    private static int countQuickFixes(
            Class<? extends InspectionProfileEntry>[] classes, PrintStream out) {
        int numQuickFixes = 0;
        for (final Class<? extends InspectionProfileEntry> aClass : classes) {
            final String className = aClass.getName();
            try {
                final InspectionProfileEntry inspection =
                        aClass.newInstance();
                if (!(inspection instanceof GlobalInspectionTool)) {
                    if (((BaseInspection) inspection).hasQuickFix()) {
                        numQuickFixes++;
                    }
                }
            } catch (InstantiationException ignore) {
                out.print(InspectionGadgetsBundle.message(
                        "create.documentation.couldn.t.instantiate.class",
                        className));
            } catch (IllegalAccessException ignore) {
                out.print(InspectionGadgetsBundle.message(
                        "create.documentation.couldnt.access.class", className));
            } catch (ClassCastException ignore) {
                out.print(InspectionGadgetsBundle.message(
                        "create.documentation.couldnt.cast.class", className));
            }
        }
        return numQuickFixes;
    }

    @NotNull
    public String getComponentName() {
        return "InspectionGadgets";
    }

    public Class<? extends InspectionProfileEntry>[] getInspectionClasses() {
        if (m_inspectionClasses.isEmpty()) {
            registerAbstractionInspections();
            registerAssignmentInspections();
            registerBitwiseInspections();
            registerBugInspections();
            registerClassLayoutInspections();
            registerClassMetricsInspections();
            registerCloneInspections();
            registerControlFlowInspections();
            registerDataFlowInspections();
            registerEncapsulationInspections();
            registerErrorHandlingInspections();
            registerFinalizationInspections();
            registerImportInspections();
            registerInheritanceInspections();
            registerInitializerInspections();
            registerInternationalInspections();
            registerJ2MEInspections();
            registerJavaBeansInspections();
            registerJdk5SpecificInspections();
            registerJdkInspections();
            registerJUnitInspections();
            registerLoggingInspections();
            registerMaturityInspections();
            registerMemoryInspections();
            registerMethodMetricsInspections();
            registerNamingInspections();
            registerNumericInspections();
            registerPerformanceInspections();
            registerPortabilityInspections();
            registerResourceManagementInspections();
            registerSecurityInspections();
            registerSerializationInspections();
            registerStyleInspections();
            registerThreadingInspections();
            registerVisibilityInspections();
            //m_inspectionClasses.add(MethodReturnAlwaysConstantInspection.class);
            m_inspectionClasses.add(BooleanMethodIsAlwaysInvertedInspection.class);
            //registerPackagingInspections();
            //registerModularizationInspections();
            //registerDependencyInspections();
        }
        final int numInspections = m_inspectionClasses.size();
        final Class<? extends InspectionProfileEntry>[] classArray =
                new Class[numInspections];
        return m_inspectionClasses.toArray(classArray);
    }

    private void registerPackagingInspections() {
        m_inspectionClasses.add(PackageInMultipleModulesInspection.class);
        m_inspectionClasses.add(DisjointPackageInspection.class);
        m_inspectionClasses.add(ClassUnconnectedToPackageInspection.class);
        m_inspectionClasses.add(PackageWithTooManyClassesInspection.class);
        m_inspectionClasses.add(PackageWithTooFewClassesInspection.class);
    }

    private void registerModularizationInspections() {
        m_inspectionClasses.add(ModuleWithTooManyClassesInspection.class);
        m_inspectionClasses.add(ModuleWithTooFewClassesInspection.class);
    }

    private void registerDependencyInspections() {
        m_inspectionClasses.add(ClassWithTooManyDependenciesInspection.class);
        m_inspectionClasses.add(ClassWithTooManyDependentsInspection.class);
        m_inspectionClasses.add(ClassWithTooManyTransitiveDependenciesInspection.class);
        m_inspectionClasses.add(ClassWithTooManyTransitiveDependentsInspection.class);
        m_inspectionClasses.add(CyclicClassDependencyInspection.class);
        m_inspectionClasses.add(CyclicPackageDependencyInspection.class);
    }

    public void initComponent() {
    }

    private void registerResourceManagementInspections() {
        m_inspectionClasses.add(ChannelResourceInspection.class);
        m_inspectionClasses.add(DriverManagerGetConnectionInspection.class);
        m_inspectionClasses.add(HibernateResourceInspection.class);
        m_inspectionClasses.add(IOResourceInspection.class);
        m_inspectionClasses.add(JDBCResourceInspection.class);
        m_inspectionClasses.add(JNDIResourceInspection.class);
        m_inspectionClasses.add(SocketResourceInspection.class);
    }

    private void registerLoggingInspections() {
        m_inspectionClasses.add(ClassWithMultipleLoggersInspection.class);
        m_inspectionClasses.add(ClassWithoutLoggerInspection.class);
        m_inspectionClasses.add(NonStaticFinalLoggerInspection.class);
        //m_inspectionClasses.add(PublicMethodWithoutLoggingInspection.class);
    }

    private void registerSecurityInspections() {
        m_inspectionClasses.add(ClassLoaderInstantiationInspection.class);
        m_inspectionClasses.add(CloneableClassInSecureContextInspection.class);
        m_inspectionClasses.add(CustomClassloaderInspection.class);
        m_inspectionClasses.add(CustomSecurityManagerInspection.class);
        m_inspectionClasses.add(DeserializableClassInSecureContextInspection.class);
        m_inspectionClasses.add(DesignForExtensionInspection.class);
        m_inspectionClasses.add(JDBCExecuteWithNonConstantStringInspection.class);
        m_inspectionClasses.add(JDBCPrepareStatementWithNonConstantStringInspection.class);
        m_inspectionClasses.add(LoadLibraryWithNonConstantStringInspection.class);
        m_inspectionClasses.add(NonFinalCloneInspection.class);
        m_inspectionClasses.add(NonStaticInnerClassInSecureContextInspection.class);
        m_inspectionClasses.add(PublicStaticArrayFieldInspection.class);
        m_inspectionClasses.add(PublicStaticCollectionFieldInspection.class);
        m_inspectionClasses.add(RuntimeExecWithNonConstantStringInspection.class);
        m_inspectionClasses.add(SerializableClassInSecureContextInspection.class);
        m_inspectionClasses.add(SystemSetSecurityManagerInspection.class);
        m_inspectionClasses.add(SystemPropertiesInspection.class);
        m_inspectionClasses.add(UnsecureRandomNumberGenerationInspection.class);
    }

    private void registerImportInspections() {
        m_inspectionClasses.add(JavaLangImportInspection.class);
        m_inspectionClasses.add(OnDemandImportInspection.class);
        m_inspectionClasses.add(RedundantImportInspection.class);
        m_inspectionClasses.add(SamePackageImportInspection.class);
        m_inspectionClasses.add(SingleClassImportInspection.class);
        m_inspectionClasses.add(StaticImportInspection.class);
        m_inspectionClasses.add(UnusedImportInspection.class);
    }

    private void registerNamingInspections() {
        m_inspectionClasses.add(AnnotationNamingConventionInspection.class);
        m_inspectionClasses.add(BooleanMethodNameMustStartWithQuestionInspection.class);
        m_inspectionClasses.add(ClassNamePrefixedWithPackageNameInspection.class);
        m_inspectionClasses.add(ClassNameSameAsAncestorNameInspection.class);
        m_inspectionClasses.add(ClassNamingConventionInspection.class);
        m_inspectionClasses.add(ConfusingMainMethodInspection.class);
        m_inspectionClasses.add(ConstantNamingConventionInspection.class);
        m_inspectionClasses.add(DollarSignInNameInspection.class);
        m_inspectionClasses.add(EnumeratedClassNamingConventionInspection.class);
        m_inspectionClasses.add(EnumeratedConstantNamingConventionInspection.class);
        m_inspectionClasses.add(ExceptionNameDoesntEndWithExceptionInspection.class);
        m_inspectionClasses.add(InstanceMethodNamingConventionInspection.class);
        m_inspectionClasses.add(InstanceVariableNamingConventionInspection.class);
        m_inspectionClasses.add(InterfaceNamingConventionInspection.class);
        m_inspectionClasses.add(LocalVariableNamingConventionInspection.class);
        m_inspectionClasses.add(MethodNameSameAsClassNameInspection.class);
        m_inspectionClasses.add(MethodNameSameAsParentNameInspection.class);
        m_inspectionClasses.add(MethodNamesDifferOnlyByCaseInspection.class);
        m_inspectionClasses.add(NonBooleanMethodNameMayNotStartWithQuestionInspection.class);
        m_inspectionClasses.add(NonExceptionNameEndsWithExceptionInspection.class);
        m_inspectionClasses.add(OverloadedMethodsWithSameNumberOfParametersInspection.class);
        m_inspectionClasses.add(OverloadedVarargsMethodInspection.class);
//        m_inspectionClasses.add(PackageNamingConventionInspection.class);
        m_inspectionClasses.add(ParameterNameDiffersFromOverriddenParameterInspection.class);
        m_inspectionClasses.add(ParameterNamingConventionInspection.class);
        m_inspectionClasses.add(QuestionableNameInspection.class);
        m_inspectionClasses.add(StandardVariableNamesInspection.class);
        m_inspectionClasses.add(StaticMethodNamingConventionInspection.class);
        m_inspectionClasses.add(StaticVariableNamingConventionInspection.class);
        m_inspectionClasses.add(TypeParameterNamingConventionInspection.class);
        m_inspectionClasses.add(UpperCaseFieldNameNotConstantInspection.class);
    }

    private void registerControlFlowInspections() {
        m_inspectionClasses.add(BreakStatementInspection.class);
        m_inspectionClasses.add(BreakStatementWithLabelInspection.class);
        m_inspectionClasses.add(ConditionalExpressionInspection.class);
        m_inspectionClasses.add(ConditionalExpressionWithIdenticalBranchesInspection.class);
        m_inspectionClasses.add(ConfusingElseInspection.class);
        m_inspectionClasses.add(ConstantConditionalExpressionInspection.class);
        m_inspectionClasses.add(ConstantIfStatementInspection.class);
        m_inspectionClasses.add(ContinueStatementInspection.class);
        m_inspectionClasses.add(ContinueStatementWithLabelInspection.class);
        m_inspectionClasses.add(DefaultNotLastCaseInSwitchInspection.class);
        m_inspectionClasses.add(DoubleNegationInspection.class);
        m_inspectionClasses.add(DuplicateBooleanBranchInspection.class);
        m_inspectionClasses.add(DuplicateConditionInspection.class);
        m_inspectionClasses.add(EnumSwitchStatementWhichMissesCasesInspection.class);
        m_inspectionClasses.add(FallthruInSwitchStatementInspection.class);
        m_inspectionClasses.add(ForLoopReplaceableByWhileInspection.class);
        m_inspectionClasses.add(ForLoopWithMissingComponentInspection.class);
        m_inspectionClasses.add(IfStatementWithIdenticalBranchesInspection.class);
        m_inspectionClasses.add(IfStatementWithTooManyBranchesInspection.class);
        m_inspectionClasses.add(InfiniteLoopStatementInspection.class);
        m_inspectionClasses.add(LabeledStatementInspection.class);
        m_inspectionClasses.add(LoopConditionNotUpdatedInsideLoopInspection.class);
        m_inspectionClasses.add(LoopStatementsThatDontLoopInspection.class);
        m_inspectionClasses.add(NegatedConditionalInspection.class);
        m_inspectionClasses.add(NegatedIfElseInspection.class);
        m_inspectionClasses.add(NestedConditionalExpressionInspection.class);
        m_inspectionClasses.add(NestedSwitchStatementInspection.class);
        m_inspectionClasses.add(OverlyComplexBooleanExpressionInspection.class);
        m_inspectionClasses.add(PointlessBooleanExpressionInspection.class);
        m_inspectionClasses.add(PointlessIndexOfComparisonInspection.class);
        m_inspectionClasses.add(SimplifiableConditionalExpressionInspection.class);
        m_inspectionClasses.add(SwitchStatementDensityInspection.class);
        m_inspectionClasses.add(SwitchStatementInspection.class);
        m_inspectionClasses.add(SwitchStatementWithConfusingDeclarationInspection.class);
        m_inspectionClasses.add(SwitchStatementWithTooFewBranchesInspection.class);
        m_inspectionClasses.add(SwitchStatementWithTooManyBranchesInspection.class);
        m_inspectionClasses.add(SwitchStatementsWithoutDefaultInspection.class);
        m_inspectionClasses.add(TrivialIfInspection.class);
        m_inspectionClasses.add(UnnecessaryConditionalExpressionInspection.class);
        m_inspectionClasses.add(UnnecessaryContinueInspection.class);
        m_inspectionClasses.add(UnnecessaryDefaultInspection.class);
        m_inspectionClasses.add(UnnecessaryLabelOnBreakStatementInspection.class);
        m_inspectionClasses.add(UnnecessaryLabelOnContinueStatementInspection.class);
        m_inspectionClasses.add(UnnecessaryReturnInspection.class);
        m_inspectionClasses.add(UnusedLabelInspection.class);
    }

    private void registerBugInspections() {
        m_inspectionClasses.add(ArchaicSystemPropertyAccessInspection.class);
        m_inspectionClasses.add(ArrayEqualsInspection.class);
        m_inspectionClasses.add(CastConflictsWithInstanceofInspection.class);
        m_inspectionClasses.add(CastToIncompatibleInterfaceInspection.class);
        m_inspectionClasses.add(CollectionAddedToSelfInspection.class);
        m_inspectionClasses.add(CompareToUsesNonFinalVariableInspection.class);
        m_inspectionClasses.add(CovariantCompareToInspection.class);
        m_inspectionClasses.add(CovariantEqualsInspection.class);
        m_inspectionClasses.add(EmptyInitializerInspection.class);
        m_inspectionClasses.add(EmptyStatementBodyInspection.class);
        m_inspectionClasses.add(EqualsBetweenInconvertibleTypesInspection.class);
        m_inspectionClasses.add(EqualsUsesNonFinalVariableInspection.class);
        m_inspectionClasses.add(EqualsWhichDoesntCheckParameterClassInspection.class);
        m_inspectionClasses.add(ForLoopThatDoesntUseLoopVariableInspection.class);
        m_inspectionClasses.add(HashCodeUsesNonFinalVariableInspection.class);
        m_inspectionClasses.add(IgnoreResultOfCallInspection.class);
        m_inspectionClasses.add(InfiniteRecursionInspection.class);
        m_inspectionClasses.add(InstanceofIncompatibleInterfaceInspection.class);
        m_inspectionClasses.add(InstantiationOfUtilityClassInspection.class);
        m_inspectionClasses.add(IteratorHasNextCallsIteratorNextInspection.class);
        m_inspectionClasses.add(IteratorNextDoesNotThrowNoSuchElementExceptionInspection.class);
        m_inspectionClasses.add(MalformedFormatStringInspection.class);
        m_inspectionClasses.add(MalformedRegexInspection.class);
        if (classExists("javax.xml.xpath.XPath")) {
            m_inspectionClasses.add(MalformedXPathInspection.class);
        }
        m_inspectionClasses.add(MismatchedArrayReadWriteInspection.class);
        m_inspectionClasses.add(MismatchedCollectionQueryUpdateInspection.class);
        m_inspectionClasses.add(MisspelledCompareToInspection.class);
        m_inspectionClasses.add(MisspelledHashcodeInspection.class);
        m_inspectionClasses.add(MisspelledEqualsInspection.class);
        m_inspectionClasses.add(MisspelledToStringInspection.class);
        m_inspectionClasses.add(NonShortCircuitBooleanInspection.class);
        m_inspectionClasses.add(NullArgumentToVariableArgMethodInspection.class);
        m_inspectionClasses.add(ObjectEqualityInspection.class);
        m_inspectionClasses.add(ObjectEqualsNullInspection.class);
        m_inspectionClasses.add(ObjectToStringInspection.class);
        m_inspectionClasses.add(PrimitiveArrayArgumentToVariableArgMethodInspection.class);
        m_inspectionClasses.add(ReflectionForUnavailableAnnotationInspection.class);
        m_inspectionClasses.add(ReplaceAllDotInspection.class);
        m_inspectionClasses.add(ResultOfObjectAllocationIgnoredInspection.class);
        m_inspectionClasses.add(ResultSetIndexZeroInspection.class);
        m_inspectionClasses.add(ReturnNullInspection.class);
        m_inspectionClasses.add(StaticCallOnSubclassInspection.class);
        m_inspectionClasses.add(StaticFieldReferenceOnSubclassInspection.class);
        m_inspectionClasses.add(StringEqualityInspection.class);
        m_inspectionClasses.add(SubtractionInCompareToInspection.class);
        m_inspectionClasses.add(SuspiciousSystemArraycopyInspection.class);
        m_inspectionClasses.add(SuspiciousToArrayCallInspection.class);
        m_inspectionClasses.add(TextLabelInSwitchStatementInspection.class);
        m_inspectionClasses.add(UseOfPropertiesAsHashtableInspection.class);

    }

    private static boolean classExists(String className) {
        final Class<?> aClass;
        try {
            aClass = Class.forName(className);
        } catch (ClassNotFoundException ignore) {
            return false;
        }
        return aClass != null;
    }

    private void registerAbstractionInspections() {
        m_inspectionClasses.add(CastToConcreteClassInspection.class);
        m_inspectionClasses.add(ClassReferencesSubclassInspection.class);
        m_inspectionClasses.add(DeclareCollectionAsInterfaceInspection.class);
        m_inspectionClasses.add(FeatureEnvyInspection.class);
        m_inspectionClasses.add(InstanceVariableOfConcreteClassInspection.class);
        m_inspectionClasses.add(InstanceofChainInspection.class);
        m_inspectionClasses.add(InstanceofInterfacesInspection.class);
        m_inspectionClasses.add(InstanceofThisInspection.class);
        m_inspectionClasses.add(LocalVariableOfConcreteClassInspection.class);
        m_inspectionClasses.add(MagicNumberInspection.class);
        m_inspectionClasses.add(MethodOnlyUsedFromInnerClassInspection.class);
        m_inspectionClasses.add(MethodReturnOfConcreteClassInspection.class);
        m_inspectionClasses.add(OverlyStrongTypeCastInspection.class);
        m_inspectionClasses.add(ParameterOfConcreteClassInspection.class);
        m_inspectionClasses.add(PublicMethodNotExposedInInterfaceInspection.class);
        m_inspectionClasses.add(StaticMethodOnlyUsedInOneClassInspection.class);
        m_inspectionClasses.add(StaticVariableOfConcreteClassInspection.class);
        //m_inspectionClasses.add(TypeMayBeWeakenedInspection.class);
    }

    private void registerAssignmentInspections() {
        m_inspectionClasses.add(AssignmentToCatchBlockParameterInspection.class);
        m_inspectionClasses.add(AssignmentToForLoopParameterInspection.class);
        m_inspectionClasses.add(AssignmentToMethodParameterInspection.class);
        m_inspectionClasses.add(AssignmentToNullInspection.class);
        m_inspectionClasses.add(AssignmentToStaticFieldFromInstanceMethodInspection.class);
        m_inspectionClasses.add(AssignmentUsedAsConditionInspection.class);
        m_inspectionClasses.add(IncrementDecrementUsedAsExpressionInspection.class);
        m_inspectionClasses.add(NestedAssignmentInspection.class);
        m_inspectionClasses.add(ReplaceAssignmentWithOperatorAssignmentInspection.class);
    }

    private void registerJdk5SpecificInspections() {
        m_inspectionClasses.add(ForCanBeForeachInspection.class);
        m_inspectionClasses.add(IndexOfReplaceableByContainsInspection.class);
        m_inspectionClasses.add(RawUseOfParameterizedTypeInspection.class);
        m_inspectionClasses.add(UnnecessaryBoxingInspection.class);
        m_inspectionClasses.add(UnnecessaryUnboxingInspection.class);
        m_inspectionClasses.add(WhileCanBeForeachInspection.class);
    }

    private void registerClassLayoutInspections() {
        m_inspectionClasses.add(AnonymousInnerClassInspection.class);
        m_inspectionClasses.add(ClassInTopLevelPackageInspection.class);
        m_inspectionClasses.add(ClassInitializerInspection.class);
        m_inspectionClasses.add(ClassMayBeInterfaceInspection.class);
        m_inspectionClasses.add(ClassNameDiffersFromFileNameInspection.class);
        m_inspectionClasses.add(ConstantDeclaredInAbstractClassInspection.class);
        m_inspectionClasses.add(ConstantDeclaredInInterfaceInspection.class);
        m_inspectionClasses.add(EmptyClassInspection.class);
        m_inspectionClasses.add(FinalClassInspection.class);
        m_inspectionClasses.add(FinalMethodInFinalClassInspection.class);
        m_inspectionClasses.add(FinalMethodInspection.class);
        m_inspectionClasses.add(FinalPrivateMethodInspection.class);
        m_inspectionClasses.add(FinalStaticMethodInspection.class);
        m_inspectionClasses.add(InnerClassOnInterfaceInspection.class);
        m_inspectionClasses.add(LimitedScopeInnerClassInspection.class);
        m_inspectionClasses.add(MarkerInterfaceInspection.class);
        m_inspectionClasses.add(MissingDeprecatedAnnotationInspection.class);
        m_inspectionClasses.add(MissingOverrideAnnotationInspection.class);
        m_inspectionClasses.add(MultipleTopLevelClassesInFileInspection.class);
        m_inspectionClasses.add(NoopMethodInAbstractClassInspection.class);
        m_inspectionClasses.add(ProtectedMemberInFinalClassInspection.class);
        m_inspectionClasses.add(PublicConstructorInNonPublicClassInspection.class);
        m_inspectionClasses.add(SingletonInspection.class);
        m_inspectionClasses.add(StaticNonFinalFieldInspection.class);
        m_inspectionClasses.add(UtilityClassInspection.class);
        m_inspectionClasses.add(UtilityClassWithPublicConstructorInspection.class);
        m_inspectionClasses.add(UtilityClassWithoutPrivateConstructorInspection.class);

    }

    private void registerInheritanceInspections() {
        m_inspectionClasses.add(AbstractClassExtendsConcreteClassInspection.class);
        m_inspectionClasses.add(AbstractClassNeverImplementedInspection.class);
        m_inspectionClasses.add(AbstractClassWithoutAbstractMethodsInspection.class);
        m_inspectionClasses.add(AbstractMethodOverridesAbstractMethodInspection.class);
        m_inspectionClasses.add(AbstractMethodOverridesConcreteMethodInspection.class);
        m_inspectionClasses.add(AbstractMethodWithMissingImplementationsInspection.class);
        m_inspectionClasses.add(ExtendsAnnotationInspection.class);
        m_inspectionClasses.add(ExtendsConcreteCollectionInspection.class);
        m_inspectionClasses.add(ExtendsUtilityClassInspection.class);
        m_inspectionClasses.add(InterfaceNeverImplementedInspection.class);
        m_inspectionClasses.add(NonProtectedConstructorInAbstractClassInspection.class);
        m_inspectionClasses.add(RedundantMethodOverrideInspection.class);
        m_inspectionClasses.add(RefusedBequestInspection.class);
        m_inspectionClasses.add(StaticInheritanceInspection.class);
        m_inspectionClasses.add(TypeParameterExtendsFinalClassInspection.class);
    }

    private void registerJavaBeansInspections() {
        m_inspectionClasses.add(ClassWithoutConstructorInspection.class);
        m_inspectionClasses.add(ClassWithoutNoArgConstructorInspection.class);
        m_inspectionClasses.add(FieldHasSetterButNoGetterInspection.class);
    }

    private void registerCloneInspections() {
        m_inspectionClasses.add(CloneCallsConstructorsInspection.class);
        m_inspectionClasses.add(CloneCallsSuperCloneInspection.class);
        m_inspectionClasses.add(CloneDeclaresCloneNotSupportedInspection.class);
        m_inspectionClasses.add(CloneInNonCloneableClassInspection.class);
        m_inspectionClasses.add(CloneableImplementsCloneInspection.class);
    }

    private void registerVisibilityInspections() {
        m_inspectionClasses.add(AnonymousClassVariableHidesContainingMethodVariableInspection.class);
        m_inspectionClasses.add(ClassEscapesItsScopeInspection.class);
        m_inspectionClasses.add(FieldHidesSuperclassFieldInspection.class);
        m_inspectionClasses.add(InnerClassVariableHidesOuterClassVariableInspection.class);
        m_inspectionClasses.add(LocalVariableHidingMemberVariableInspection.class);
        m_inspectionClasses.add(MethodOverridesPackageLocalMethodInspection.class);
        m_inspectionClasses.add(MethodOverloadsParentMethodInspection.class);
        m_inspectionClasses.add(MethodOverridesPrivateMethodInspection.class);
        m_inspectionClasses.add(MethodOverridesStaticMethodInspection.class);
        m_inspectionClasses.add(TypeParameterHidesVisibleTypeInspection.class);
        m_inspectionClasses.add(ParameterHidingMemberVariableInspection.class);
    }

    private void registerEncapsulationInspections() {
        m_inspectionClasses.add(AssignmentToCollectionFieldFromParameterInspection.class);
        m_inspectionClasses.add(AssignmentToDateFieldFromParameterInspection.class);
        m_inspectionClasses.add(PackageVisibleFieldInspection.class);
        m_inspectionClasses.add(PackageVisibleInnerClassInspection.class);
        m_inspectionClasses.add(ProtectedFieldInspection.class);
        m_inspectionClasses.add(ProtectedInnerClassInspection.class);
        m_inspectionClasses.add(PublicFieldInspection.class);
        m_inspectionClasses.add(PublicInnerClassInspection.class);
        m_inspectionClasses.add(ReturnOfCollectionFieldInspection.class);
        m_inspectionClasses.add(ReturnOfDateFieldInspection.class);
        m_inspectionClasses.add(UseOfAnotherObjectsPrivateFieldInspection.class);
    }

    private void registerInitializerInspections() {
        m_inspectionClasses.add(AbstractMethodCallInConstructorInspection.class);
        m_inspectionClasses.add(InstanceVariableInitializationInspection.class);
        m_inspectionClasses.add(InstanceVariableUninitializedUseInspection.class);
        m_inspectionClasses.add(NonFinalStaticVariableUsedInClassInitializationInspection.class);
        m_inspectionClasses.add(NonThreadSafeLazyInitializationInspection.class);
        m_inspectionClasses.add(OverridableMethodCallDuringObjectConstructionInspection.class);
        m_inspectionClasses.add(OverriddenMethodCallInConstructorInspection.class);
        m_inspectionClasses.add(StaticVariableInitializationInspection.class);
        m_inspectionClasses.add(StaticVariableUninitializedUseInspection.class);
        m_inspectionClasses.add(ThisEscapedInConstructorInspection.class);
    }

    private void registerBitwiseInspections() {
        m_inspectionClasses.add(IncompatibleMaskInspection.class);
        m_inspectionClasses.add(PointlessBitwiseExpressionInspection.class);
        m_inspectionClasses.add(ShiftOutOfRangeInspection.class);
    }

    private void registerStyleInspections() {
        m_inspectionClasses.add(CStyleArrayDeclarationInspection.class);
        m_inspectionClasses.add(ChainedEqualityInspection.class);
        m_inspectionClasses.add(ChainedMethodCallInspection.class);
        m_inspectionClasses.add(ConfusingOctalEscapeInspection.class);
        m_inspectionClasses.add(ConstantOnLHSOfComparisonInspection.class);
        m_inspectionClasses.add(ConstantOnRHSOfComparisonInspection.class);
        m_inspectionClasses.add(ControlFlowStatementWithoutBracesInspection.class);
        m_inspectionClasses.add(ExtendsObjectInspection.class);
        m_inspectionClasses.add(ImplicitCallToSuperInspection.class);
        m_inspectionClasses.add(LiteralAsArgToStringEqualsInspection.class);
        m_inspectionClasses.add(MissortedModifiersInspection.class);
        m_inspectionClasses.add(MultipleDeclarationInspection.class);
        m_inspectionClasses.add(MultipleTypedDeclarationInspection.class);
        m_inspectionClasses.add(NestedMethodCallInspection.class);
        m_inspectionClasses.add(RedundantFieldInitializationInspection.class);
        m_inspectionClasses.add(RedundantImplementsInspection.class);
        m_inspectionClasses.add(ReturnThisInspection.class);
        m_inspectionClasses.add(TypeParameterExtendsObjectInspection.class);
        m_inspectionClasses.add(UnnecessarilyQualifiedStaticUsageInspection.class);
        m_inspectionClasses.add(UnnecessaryBlockStatementInspection.class);
        m_inspectionClasses.add(UnnecessaryConstructorInspection.class);
        m_inspectionClasses.add(UnnecessaryEnumModifierInspection.class);
        m_inspectionClasses.add(UnnecessaryFinalOnLocalVariableInspection.class);
        m_inspectionClasses.add(UnnecessaryFinalOnParameterInspection.class);
        m_inspectionClasses.add(UnnecessaryFullyQualifiedNameInspection.class);
        m_inspectionClasses.add(UnnecessaryInterfaceModifierInspection.class);
        m_inspectionClasses.add(UnnecessaryParenthesesInspection.class);
        m_inspectionClasses.add(UnnecessaryQualifierForThisInspection.class);
        m_inspectionClasses.add(UnnecessarySemicolonInspection.class);
        m_inspectionClasses.add(UnnecessarySuperConstructorInspection.class);
        m_inspectionClasses.add(UnnecessaryThisInspection.class);
        m_inspectionClasses.add(UnqualifiedStaticUsageInspection.class);
    }

    private void registerDataFlowInspections() {
        m_inspectionClasses.add(ReuseOfLocalVariableInspection.class);
        m_inspectionClasses.add(TooBroadScopeInspection.class);
        m_inspectionClasses.add(UnnecessaryLocalVariableInspection.class);
    }

    private void registerErrorHandlingInspections() {
        m_inspectionClasses.add(BadExceptionCaughtInspection.class);
        m_inspectionClasses.add(BadExceptionDeclaredInspection.class);
        m_inspectionClasses.add(BadExceptionThrownInspection.class);
        m_inspectionClasses.add(CatchGenericClassInspection.class);
        m_inspectionClasses.add(CheckedExceptionClassInspection.class);
        m_inspectionClasses.add(ContinueOrBreakFromFinallyBlockInspection.class);
        m_inspectionClasses.add(EmptyCatchBlockInspection.class);
        m_inspectionClasses.add(EmptyFinallyBlockInspection.class);
        m_inspectionClasses.add(EmptyTryBlockInspection.class);
        m_inspectionClasses.add(ErrorRethrownInspection.class);
        m_inspectionClasses.add(ExceptionFromCatchWhichDoesntWrapInspection.class);
        m_inspectionClasses.add(FinallyBlockCannotCompleteNormallyInspection.class);
        m_inspectionClasses.add(InstanceofCatchParameterInspection.class);
        m_inspectionClasses.add(NestedTryStatementInspection.class);
        m_inspectionClasses.add(NonFinalFieldOfExceptionInspection.class);
        m_inspectionClasses.add(ReturnFromFinallyBlockInspection.class);
        m_inspectionClasses.add(ThreadDeathRethrownInspection.class);
        m_inspectionClasses.add(ThrowCaughtLocallyInspection.class);
        m_inspectionClasses.add(ThrowFromFinallyBlockInspection.class);
        m_inspectionClasses.add(TooBroadCatchInspection.class);
        m_inspectionClasses.add(UncheckedExceptionClassInspection.class);
        m_inspectionClasses.add(UnusedCatchParameterInspection.class);
    }

    private void registerFinalizationInspections() {
        m_inspectionClasses.add(FinalizeCallsSuperFinalizeInspection.class);
        m_inspectionClasses.add(FinalizeInspection.class);
        m_inspectionClasses.add(FinalizeNotProtectedInspection.class);
        m_inspectionClasses.add(NoExplicitFinalizeCallsInspection.class);
    }

    private void registerSerializationInspections() {
        m_inspectionClasses.add(ComparatorNotSerializableInspection.class);
        m_inspectionClasses.add(ExternalizableWithSerializationMethodsInspection.class);
        m_inspectionClasses.add(NonSerializableFieldInSerializableClassInspection.class);
        m_inspectionClasses.add(NonSerializableObjectBoundToHttpSessionInspection.class);
        m_inspectionClasses.add(NonSerializableObjectPassedToObjectStreamInspection.class);
        m_inspectionClasses.add(NonSerializableWithSerialVersionUIDFieldInspection.class);
        m_inspectionClasses.add(NonSerializableWithSerializationMethodsInspection.class);
        m_inspectionClasses.add(ReadObjectAndWriteObjectPrivateInspection.class);
        m_inspectionClasses.add(ReadObjectInitializationInspection.class);
        m_inspectionClasses.add(ReadResolveAndWriteReplaceProtectedInspection.class);
        m_inspectionClasses.add(SerialPersistentFieldsWithWrongSignatureInspection.class);
        m_inspectionClasses.add(SerialVersionUIDNotStaticFinalInspection.class);
        m_inspectionClasses.add(SerializableHasSerialVersionUIDFieldInspection.class);
        m_inspectionClasses.add(SerializableHasSerializationMethodsInspection.class);
        m_inspectionClasses.add(SerializableInnerClassHasSerialVersionUIDFieldInspection.class);
        m_inspectionClasses.add(SerializableInnerClassWithNonSerializableOuterClassInspection.class);
        m_inspectionClasses.add(SerializableWithUnconstructableAncestorInspection.class);
        m_inspectionClasses.add(TransientFieldInNonSerializableClassInspection.class);
    }

    private void registerThreadingInspections() {
        m_inspectionClasses.add(AccessToStaticFieldLockedOnInstanceInspection.class);
        m_inspectionClasses.add(ArithmeticOnVolatileFieldInspection.class);
        m_inspectionClasses.add(AwaitNotInLoopInspection.class);
        m_inspectionClasses.add(AwaitWithoutCorrespondingSignalInspection.class);
        m_inspectionClasses.add(BusyWaitInspection.class);
        m_inspectionClasses.add(CallToNativeMethodWhileLockedInspection.class);
        m_inspectionClasses.add(ConditionSignalInspection.class);
        m_inspectionClasses.add(DoubleCheckedLockingInspection.class);
        m_inspectionClasses.add(EmptySynchronizedStatementInspection.class);
        m_inspectionClasses.add(ExtendsThreadInspection.class);
        m_inspectionClasses.add(FieldAccessedSynchronizedAndUnsynchronizedInspection.class);
        m_inspectionClasses.add(NakedNotifyInspection.class);
        m_inspectionClasses.add(NestedSynchronizedStatementInspection.class);
        m_inspectionClasses.add(NonSynchronizedMethodOverridesSynchronizedMethodInspection.class);
        m_inspectionClasses.add(NotifyCalledOnConditionInspection.class);
        m_inspectionClasses.add(NotifyNotInSynchronizedContextInspection.class);
        m_inspectionClasses.add(NotifyWithoutCorrespondingWaitInspection.class);
        m_inspectionClasses.add(ObjectNotifyInspection.class);
        m_inspectionClasses.add(PublicFieldAccessedInSynchronizedContextInspection.class);
        m_inspectionClasses.add(SafeLockInspection.class);
        m_inspectionClasses.add(SignalWithoutCorrespondingAwaitInspection.class);
        m_inspectionClasses.add(SleepWhileHoldingLockInspection.class);
        m_inspectionClasses.add(SynchronizeOnLockInspection.class);
        m_inspectionClasses.add(SynchronizeOnNonFinalFieldInspection.class);
        m_inspectionClasses.add(SynchronizeOnThisInspection.class);
        m_inspectionClasses.add(SynchronizedMethodInspection.class);
        m_inspectionClasses.add(SystemRunFinalizersOnExitInspection.class);
        m_inspectionClasses.add(ThreadPriorityInspection.class);
        m_inspectionClasses.add(ThreadRunInspection.class);
        m_inspectionClasses.add(ThreadStartInConstructionInspection.class);
        m_inspectionClasses.add(ThreadStopSuspendResumeInspection.class);
        m_inspectionClasses.add(ThreadWithDefaultRunMethodInspection.class);
        m_inspectionClasses.add(ThreadYieldInspection.class);
        m_inspectionClasses.add(UnconditionalWaitInspection.class);
        m_inspectionClasses.add(VolatileArrayFieldInspection.class);
        m_inspectionClasses.add(VolatileLongOrDoubleFieldInspection.class);
        m_inspectionClasses.add(WaitCalledOnConditionInspection.class);
        m_inspectionClasses.add(WaitNotInLoopInspection.class);
        m_inspectionClasses.add(WaitNotInSynchronizedContextInspection.class);
        m_inspectionClasses.add(WaitOrAwaitWithoutTimeoutInspection.class);
        m_inspectionClasses.add(WaitWhileHoldingTwoLocksInspection.class);
        m_inspectionClasses.add(WaitWithoutCorrespondingNotifyInspection.class);
        m_inspectionClasses.add(WhileLoopSpinsOnFieldInspection.class);
    }

    private void registerMethodMetricsInspections() {
        m_inspectionClasses.add(CyclomaticComplexityInspection.class);
        m_inspectionClasses.add(MethodCouplingInspection.class);
        m_inspectionClasses.add(MethodWithMultipleLoopsInspection.class);
        m_inspectionClasses.add(MultipleReturnPointsPerMethodInspection.class);
        m_inspectionClasses.add(NestingDepthInspection.class);
        m_inspectionClasses.add(NonCommentSourceStatementsInspection.class);
        m_inspectionClasses.add(ParametersPerMethodInspection.class);
        m_inspectionClasses.add(ThreeNegationsPerMethodInspection.class);
        m_inspectionClasses.add(ThrownExceptionsPerMethodInspection.class);
    }

    private void registerClassMetricsInspections() {
        m_inspectionClasses.add(AnonymousClassComplexityInspection.class);
        m_inspectionClasses.add(AnonymousClassMethodCountInspection.class);
        m_inspectionClasses.add(ClassComplexityInspection.class);
        m_inspectionClasses.add(ClassCouplingInspection.class);
        m_inspectionClasses.add(ClassInheritanceDepthInspection.class);
        m_inspectionClasses.add(ClassNestingDepthInspection.class);
        m_inspectionClasses.add(ConstructorCountInspection.class);
        m_inspectionClasses.add(FieldCountInspection.class);
        m_inspectionClasses.add(MethodCountInspection.class);
    }

    private void registerPortabilityInspections() {
        m_inspectionClasses.add(HardcodedFileSeparatorsInspection.class);
        m_inspectionClasses.add(HardcodedLineSeparatorsInspection.class);
        m_inspectionClasses.add(NativeMethodsInspection.class);
        m_inspectionClasses.add(RuntimeExecInspection.class);
        m_inspectionClasses.add(SystemExitInspection.class);
        m_inspectionClasses.add(SystemGetenvInspection.class);
        m_inspectionClasses.add(UseOfAWTPeerClassInspection.class);
        m_inspectionClasses.add(UseOfJDBCDriverClassInspection.class);
        m_inspectionClasses.add(UseOfProcessBuilderInspection.class);
        m_inspectionClasses.add(UseOfSunClassesInspection.class);
    }

    private void registerJdkInspections() {
        m_inspectionClasses.add(AnnotationClassInspection.class);
        m_inspectionClasses.add(AnnotationInspection.class);
        m_inspectionClasses.add(AssertAsNameInspection.class);
        m_inspectionClasses.add(AssertStatementInspection.class);
        m_inspectionClasses.add(AutoBoxingInspection.class);
        m_inspectionClasses.add(AutoUnboxingInspection.class);
        m_inspectionClasses.add(EnumAsNameInspection.class);
        m_inspectionClasses.add(EnumClassInspection.class);
        m_inspectionClasses.add(ForeachStatementInspection.class);
        m_inspectionClasses.add(VarargParameterInspection.class);
    }

    private void registerInternationalInspections() {
        m_inspectionClasses.add(CharacterComparisonInspection.class);
        m_inspectionClasses.add(DateToStringInspection.class);
        m_inspectionClasses.add(MagicCharacterInspection.class);
        m_inspectionClasses.add(NumericToStringInspection.class);
        m_inspectionClasses.add(SimpleDateFormatWithoutLocaleInspection.class);
        m_inspectionClasses.add(StringCompareToInspection.class);
        m_inspectionClasses.add(StringConcatenationInspection.class);
        m_inspectionClasses.add(StringEqualsIgnoreCaseInspection.class);
        m_inspectionClasses.add(StringEqualsInspection.class);
        m_inspectionClasses.add(StringToUpperWithoutLocaleInspection.class);
        m_inspectionClasses.add(StringTokenizerInspection.class);
        m_inspectionClasses.add(TimeToStringInspection.class);
    }

    private void registerPerformanceInspections() {
        m_inspectionClasses.add(BooleanConstructorInspection.class);
        m_inspectionClasses.add(CallToSimpleGetterInClassInspection.class);
        m_inspectionClasses.add(CallToSimpleSetterInClassInspection.class);
        m_inspectionClasses.add(CollectionsMustHaveInitialCapacityInspection.class);
        m_inspectionClasses.add(ConstantStringInternInspection.class);
        m_inspectionClasses.add(FieldMayBeStaticInspection.class);
        m_inspectionClasses.add(InnerClassMayBeStaticInspection.class);
        m_inspectionClasses.add(InstantiatingObjectToGetClassObjectInspection.class);
        m_inspectionClasses.add(JavaLangReflectInspection.class);
        m_inspectionClasses.add(LengthOneStringInIndexOfInspection.class);
        m_inspectionClasses.add(LengthOneStringsInConcatenationInspection.class);
        m_inspectionClasses.add(ManualArrayCopyInspection.class);
        m_inspectionClasses.add(MapReplaceableByEnumMapInspection.class);
        m_inspectionClasses.add(MethodMayBeStaticInspection.class);
        m_inspectionClasses.add(MultiplyOrDivideByPowerOfTwoInspection.class);
        m_inspectionClasses.add(ObjectAllocationInLoopInspection.class);
        m_inspectionClasses.add(RandomDoubleForRandomIntegerInspection.class);
        m_inspectionClasses.add(SetReplaceableByEnumSetInspection.class);
        m_inspectionClasses.add(SizeReplaceableByIsEmptyInspection.class);
        m_inspectionClasses.add(StringBufferMustHaveInitialCapacityInspection.class);
        m_inspectionClasses.add(StringBufferReplaceableByStringBuilderInspection.class);
        m_inspectionClasses.add(StringBufferReplaceableByStringInspection.class);
        m_inspectionClasses.add(StringBufferToStringInConcatenationInspection.class);
        m_inspectionClasses.add(StringConcatenationInLoopsInspection.class);
        m_inspectionClasses.add(StringConcatenationInsideStringBufferAppendInspection.class);
        m_inspectionClasses.add(StringConstructorInspection.class);
        m_inspectionClasses.add(StringEqualsEmptyStringInspection.class);
        m_inspectionClasses.add(StringReplaceableByStringBufferInspection.class);
        m_inspectionClasses.add(StringToStringInspection.class);
        m_inspectionClasses.add(SubstringZeroInspection.class);
        m_inspectionClasses.add(TailRecursionInspection.class);
        m_inspectionClasses.add(TrivialStringConcatenationInspection.class);
        m_inspectionClasses.add(UnnecessaryTemporaryOnConversionToStringInspection.class);
        m_inspectionClasses.add(UnnecessaryTemporaryOnConversionFromStringInspection.class);
    }

    private void registerMemoryInspections() {
        m_inspectionClasses.add(StaticCollectionInspection.class);
        m_inspectionClasses.add(StringBufferFieldInspection.class);
        m_inspectionClasses.add(SystemGCInspection.class);
        m_inspectionClasses.add(ZeroLengthArrayInitializationInspection.class);
    }

    private void registerJ2MEInspections() {
        m_inspectionClasses.add(AbstractClassWithOnlyOneDirectInheritorInspection.class);
        m_inspectionClasses.add(AnonymousInnerClassMayBeStaticInspection.class);
        m_inspectionClasses.add(ArrayLengthInLoopConditionInspection.class);
        m_inspectionClasses.add(CheckForOutOfMemoryOnLargeArrayAllocationInspection.class);
        m_inspectionClasses.add(ConnectionResourceInspection.class);
        m_inspectionClasses.add(FieldRepeatedlyAccessedInspection.class);
        m_inspectionClasses.add(InterfaceWithOnlyOneDirectInheritorInspection.class);
        m_inspectionClasses.add(MethodCallInLoopConditionInspection.class);
        m_inspectionClasses.add(OverlyLargePrimitiveArrayInitializerInspection.class);
        m_inspectionClasses.add(PrivateMemberAccessBetweenOuterAndInnerClassInspection.class);
        m_inspectionClasses.add(RecordStoreResourceInspection.class);
        m_inspectionClasses.add(SimplifiableIfStatementInspection.class);
        m_inspectionClasses.add(SingleCharacterStartsWithInspection.class);
    }

    private void registerMaturityInspections() {
        m_inspectionClasses.add(SuppressionAnnotationInspection.class);
        m_inspectionClasses.add(SystemOutErrInspection.class);
        m_inspectionClasses.add(ThrowablePrintStackTraceInspection.class);
        m_inspectionClasses.add(TodoCommentInspection.class);
        m_inspectionClasses.add(ThreadDumpStackInspection.class);
        m_inspectionClasses.add(ClassWithoutToStringInspection.class);
        m_inspectionClasses.add(ObsoleteCollectionInspection.class);
    }

    private void registerNumericInspections() {
        m_inspectionClasses.add(BadOddnessInspection.class);
        m_inspectionClasses.add(BigDecimalEqualsInspection.class);
        m_inspectionClasses.add(CachedNumberConstructorCallInspection.class);
        m_inspectionClasses.add(CastThatLosesPrecisionInspection.class);
        m_inspectionClasses.add(ComparisonOfShortAndCharInspection.class);
        m_inspectionClasses.add(ComparisonToNaNInspection.class);
        m_inspectionClasses.add(ConfusingFloatingPointLiteralInspection.class);
        m_inspectionClasses.add(ConstantMathCallInspection.class);
        m_inspectionClasses.add(DivideByZeroInspection.class);
        m_inspectionClasses.add(FloatingPointEqualityInspection.class);
        m_inspectionClasses.add(ImplicitNumericConversionInspection.class);
        m_inspectionClasses.add(IntegerDivisionInFloatingPointContextInspection.class);
        m_inspectionClasses.add(IntegerMultiplicationImplicitCastToLongInspection.class);
        m_inspectionClasses.add(LongLiteralsEndingWithLowercaseLInspection.class);
        m_inspectionClasses.add(NonReproducibleMathCallInspection.class);
        m_inspectionClasses.add(OctalLiteralInspection.class);
        m_inspectionClasses.add(OctalAndDecimalIntegersMixedInspection.class);
        m_inspectionClasses.add(OverlyComplexArithmeticExpressionInspection.class);
        m_inspectionClasses.add(PointlessArithmeticExpressionInspection.class);
        m_inspectionClasses.add(UnaryPlusInspection.class);
    }

    private void registerJUnitInspections() {
        m_inspectionClasses.add(AssertsWithoutMessagesInspection.class);
        m_inspectionClasses.add(BeforeClassOrAfterClassIsPublicStaticVoidNoArgInspection.class);
        m_inspectionClasses.add(BeforeOrAfterIsPublicVoidNoArgInspection.class);
        m_inspectionClasses.add(JUnitAbstractTestClassNamingConventionInspection.class);
        m_inspectionClasses.add(JUnitTestClassNamingConventionInspection.class);
        m_inspectionClasses.add(MisspelledSetUpInspection.class);
        m_inspectionClasses.add(MisspelledTearDownInspection.class);
        m_inspectionClasses.add(MisorderedAssertEqualsParametersInspection.class);
        m_inspectionClasses.add(TestCaseWithConstructorInspection.class);
        m_inspectionClasses.add(SetupCallsSuperSetupInspection.class);
        m_inspectionClasses.add(SetupIsPublicVoidNoArgInspection.class);
        m_inspectionClasses.add(SimplifiableJUnitAssertionInspection.class);
        m_inspectionClasses.add(StaticSuiteInspection.class);
        m_inspectionClasses.add(TestCaseInProductCodeInspection.class);
        m_inspectionClasses.add(TestCaseWithNoTestMethodsInspection.class);
        m_inspectionClasses.add(TeardownCallsSuperTeardownInspection.class);
        m_inspectionClasses.add(TeardownIsPublicVoidNoArgInspection.class);
        m_inspectionClasses.add(TestMethodInProductCodeInspection.class);
        m_inspectionClasses.add(TestMethodIsPublicVoidNoArgInspection.class);
        m_inspectionClasses.add(TestMethodWithoutAssertionInspection.class);
        m_inspectionClasses.add(UnconstructableTestCaseInspection.class);
    }

    public void disposeComponent() {
    }

    public static boolean isTelemetryEnabled() {
        return TELEMETRY_ENABLED;
    }

    public InspectionGadgetsTelemetry getTelemetry() {
        return telemetry;
    }
}