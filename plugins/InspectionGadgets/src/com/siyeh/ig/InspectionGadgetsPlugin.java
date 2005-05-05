package com.siyeh.ig;

import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.siyeh.ig.abstraction.*;
import com.siyeh.ig.bugs.*;
import com.siyeh.ig.classlayout.*;
import com.siyeh.ig.classmetrics.*;
import com.siyeh.ig.cloneable.*;
import com.siyeh.ig.confusing.*;
import com.siyeh.ig.encapsulation.*;
import com.siyeh.ig.errorhandling.*;
import com.siyeh.ig.finalization.FinalizeCallsSuperFinalizeInspection;
import com.siyeh.ig.finalization.FinalizeInspection;
import com.siyeh.ig.finalization.FinalizeNotProtectedInspection;
import com.siyeh.ig.finalization.NoExplicitFinalizeCallsInspection;
import com.siyeh.ig.imports.*;
import com.siyeh.ig.initialization.*;
import com.siyeh.ig.internationalization.*;
import com.siyeh.ig.jdk.*;
import com.siyeh.ig.junit.*;
import com.siyeh.ig.logging.ClassWithMultipleLoggersInspection;
import com.siyeh.ig.logging.ClassWithoutLoggerInspection;
import com.siyeh.ig.logging.NonStaticFinalLoggerInspection;
import com.siyeh.ig.maturity.*;
import com.siyeh.ig.methodmetrics.*;
import com.siyeh.ig.naming.*;
import com.siyeh.ig.performance.*;
import com.siyeh.ig.portability.*;
import com.siyeh.ig.resources.*;
import com.siyeh.ig.security.*;
import com.siyeh.ig.serialization.*;
import com.siyeh.ig.style.*;
import com.siyeh.ig.threading.*;
import com.siyeh.ig.verbose.*;
import com.siyeh.ig.visibility.*;
import com.siyeh.ig.telemetry.InspectionGadgetsTelemetry;
import com.siyeh.ig.j2me.*;

import java.io.*;
import java.util.*;

public class InspectionGadgetsPlugin implements ApplicationComponent,
                                                InspectionToolProvider{
    private static final int NUM_INSPECTIONS = 420;
    private final List<Class> m_inspectionClasses = new ArrayList<Class>(NUM_INSPECTIONS);
    private static final String DESCRIPTION_DIRECTORY_NAME =
            "C:/My Open Source Projects/InspectionGadgetsSVN/src/inspectionDescriptions/";
    private final InspectionGadgetsTelemetry telemetry = new InspectionGadgetsTelemetry();
    private static final boolean TELEMETRY_ENABLED = true;
        
    public static void main(String[] args){
        final InspectionGadgetsPlugin plugin = new InspectionGadgetsPlugin();
        final PrintStream out;
        if(args.length == 0){
            out = System.out;
        } else{
            final OutputStream stream;
            try{
                stream = new FileOutputStream(args[0]);
            } catch(final FileNotFoundException e){
                return;
            }
            out = new PrintStream(stream);
        }
        plugin.createDocumentation(out);
    }

    private void createDocumentation(PrintStream out){
        initComponent();
        final Class[] classes = getInspectionClasses();
        String currentGroupName = "";

        final int numQuickFixes = countQuickFixes(classes, out);
        out.println(classes.length + " Inspections");
        out.println(numQuickFixes + " Quick Fixes");

        for(final Class aClass : classes){
            final String className = aClass.getName();
            try{
                final LocalInspectionTool inspection =
                        (LocalInspectionTool) aClass.newInstance();
                final String groupDisplayName =
                        inspection.getGroupDisplayName();
                if(!groupDisplayName.equals(currentGroupName)){
                    currentGroupName = groupDisplayName;
                    out.println();
                    out.print("   * ");
                    out.println(currentGroupName);
                }
                printInspectionDescription(inspection, out);
            } catch(InstantiationException e){
                out.print("Couldn't instantiate ");
                out.println(className);
            } catch(IllegalAccessException e){
                out.print("Couldn't access ");
                out.println(className);
            } catch(ClassCastException e){
                out.print("Couldn't cast ");
                out.println(className);
            }
        }

        out.println();
        out.println("Inspections enabled by default:");
        for(final Class aClass : classes){
            final String className = aClass.getName();
            try{
                final LocalInspectionTool inspection =
                        (LocalInspectionTool) aClass.newInstance();
                if(inspection.isEnabledByDefault()){
                    out.println('\t' + inspection.getDisplayName());
                }
            } catch(InstantiationException e){
                out.print("Couldn't instantiate ");
                out.println(className);
            } catch(IllegalAccessException e){
                out.print("Couldn't access ");
                out.println(className);
            } catch(ClassCastException e){
                out.print("Couldn't cast ");
                out.println(className);
            }
        }
        final File descriptionDirectory = new File(DESCRIPTION_DIRECTORY_NAME);
        final File[] descriptionFiles = descriptionDirectory.listFiles();
        final Set<File> descriptionFilesSet = new HashSet<File>(descriptionFiles.length);
        for(File descriptionFile1 : descriptionFiles){
            if(!descriptionFile1.getName().startsWith(".")){
                descriptionFilesSet.add(descriptionFile1);
            }
        }
        for(final Class aClass : classes){
            final String className = aClass.getName();
            final String simpleClassName =
                    className.substring(className.lastIndexOf('.') + 1,
                                        className.length() -
                                                "Inspection".length());
            final String fileName =
                    DESCRIPTION_DIRECTORY_NAME + simpleClassName + ".html";
            final File descriptionFile = new File(fileName);
            if(descriptionFile.exists()){
                descriptionFilesSet.remove(descriptionFile);
            } else{
                out.println("Couldn't find documentation file: " + fileName);
            }
        }
        for(final File file : descriptionFilesSet){
            out.println("Unused documentation file: " + file.getAbsolutePath());
        }
    }

    private static void printInspectionDescription(LocalInspectionTool inspection,
                                                   PrintStream out){
        final boolean hasQuickFix = ((BaseInspection) inspection).hasQuickFix();

        final String displayName = inspection.getDisplayName();
        out.print("      * ");
        out.print(displayName);
        if(hasQuickFix){
            if(((BaseInspection) inspection).buildQuickFixesOnlyForOnTheFlyErrors()){
                out.print("(r)");
            } else{
                out.print("(*)");
            }
        }
        out.println();
    }

    private static int countQuickFixes(Class[] classes, PrintStream out){
        int numQuickFixes = 0;
        for(final Class aClass : classes){
            final String className = aClass.getName();
            try{
                final LocalInspectionTool inspection =
                        (LocalInspectionTool) aClass.newInstance();
                if(((BaseInspection) inspection).hasQuickFix()){
                    numQuickFixes++;
                }
            } catch(InstantiationException e){
                out.print("Couldn't instantiate ");
                out.println(className);
            } catch(IllegalAccessException e){
                out.print("Couldn't access ");
                out.println(className);
            } catch(ClassCastException e){
                out.print("Couldn't cast ");
                out.println(className);
            }
        }
        return numQuickFixes;
    }

    public static InspectionGadgetsPlugin getInstance(){
        final Application application = ApplicationManager.getApplication();
        return application.getComponent(InspectionGadgetsPlugin.class);
    }

    public String getComponentName(){
        return "InspectionGadgets";
    }

    public Class[] getInspectionClasses(){
        final int numInspections = m_inspectionClasses.size();
        return m_inspectionClasses.toArray(new Class[numInspections]);
    }

    public void initComponent(){
        registerNamingInspections();
        registerBugInspections();
        registerCloneInspections();
        registerConfusingInspections();
        registerAbstractionInspections();
        registerClassLayoutInspections();
        //registerImportInspections();
        registerEncapsulationInspections();
        registerVisibilityInspections();
        registerInitializerInspections();
        registerFinalizationInspections();
        registerExceptionInspections();
        registerVerboseInspections();
        registerStyleInspections();
        registerSerializationInspections();
        registerThreadingInspections();
        registerMethodMetricsInspections();
        registerClassMetricsInspections();
        registerPortabilityInspections();
        registerInternationalInspections();
        registerPerformanceInspections();
        registerMaturityInspections();
        registerJUnitInspections();
        registerLoggingInspections();
        registerSecurityInspections();
        registerResourceManagementInspections();
        registerJ2MEInspections();
        Collections.sort(m_inspectionClasses, new InspectionComparator());
    }

    private void registerResourceManagementInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(HibernateResourceInspection.class);
        inspectionClasses.add(JNDIResourceInspection.class);
        inspectionClasses.add(SocketResourceInspection.class);
        inspectionClasses.add(IOResourceInspection.class);
        inspectionClasses.add(JDBCResourceInspection.class);
        inspectionClasses.add(ChannelResourceInspection.class);
    }

    private void registerLoggingInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(ClassWithoutLoggerInspection.class);
        inspectionClasses.add(ClassWithMultipleLoggersInspection.class);
        inspectionClasses.add(NonStaticFinalLoggerInspection.class);
    }

    private void registerSecurityInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(DeserializableClassInSecureContextInspection.class);
        inspectionClasses.add(SerializableClassInSecureContextInspection.class);
        inspectionClasses.add(CloneableClassInSecureContextInspection.class);
        inspectionClasses.add(NonStaticInnerClassInSecureContextInspection.class);
        inspectionClasses.add(RuntimeExecWithNonConstantStringInspection.class);
        inspectionClasses.add(LoadLibraryWithNonConstantStringInspection.class);
        inspectionClasses.add(JDBCExecuteWithNonConstantStringInspection.class);
        inspectionClasses.add(JDBCPrepareStatementWithNonConstantStringInspection.class);
        inspectionClasses.add(CustomClassloaderInspection.class);
        inspectionClasses.add(CustomSecurityManagerInspection.class);
        inspectionClasses.add(SystemSetSecurityManagerInspection.class);
        inspectionClasses.add(ClassLoaderInstantiationInspection.class);
        inspectionClasses.add(UnsecureRandomNumberGenerationInspection.class);
        inspectionClasses.add(SystemPropertiesInspection.class);
    }

    private void registerImportInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(UnusedImportInspection.class);
        inspectionClasses.add(RedundantImportInspection.class);
        inspectionClasses.add(OnDemandImportInspection.class);
        inspectionClasses.add(SingleClassImportInspection.class);
        inspectionClasses.add(JavaLangImportInspection.class);
        inspectionClasses.add(SamePackageImportInspection.class);
        inspectionClasses.add(StaticImportInspection.class);
    }

    private void registerNamingInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(JUnitTestClassNamingConventionInspection.class);
        inspectionClasses.add(JUnitAbstractTestClassNamingConventionInspection.class);
        inspectionClasses.add(ClassNamingConventionInspection.class);
        inspectionClasses.add(EnumeratedClassNamingConventionInspection.class);
        inspectionClasses.add(EnumeratedConstantNamingConventionInspection.class);
        inspectionClasses.add(AnnotationNamingConventionInspection.class);
        inspectionClasses.add(InterfaceNamingConventionInspection.class);
        inspectionClasses.add(InstanceVariableNamingConventionInspection.class);
        inspectionClasses.add(StaticVariableNamingConventionInspection.class);
        inspectionClasses.add(ConstantNamingConventionInspection.class);
        inspectionClasses.add(InstanceMethodNamingConventionInspection.class);
        inspectionClasses.add(StaticMethodNamingConventionInspection.class);
        inspectionClasses.add(LocalVariableNamingConventionInspection.class);
        inspectionClasses.add(ParameterNamingConventionInspection.class);
        inspectionClasses.add(ParameterNameDiffersFromOverriddenParameterInspection.class);
        inspectionClasses.add(ExceptionNameDoesntEndWithExceptionInspection.class);
        inspectionClasses.add(NonExceptionNameEndsWithExceptionInspection.class);
        inspectionClasses.add(ClassNamePrefixedWithPackageNameInspection.class);
        inspectionClasses.add(ClassNameSameAsAncestorNameInspection.class);
        inspectionClasses.add(MethodNameSameAsClassNameInspection.class);
        inspectionClasses.add(MethodNameSameAsParentNameInspection.class);
        inspectionClasses.add(StandardVariableNamesInspection.class);
        inspectionClasses.add(BooleanMethodNameMustStartWithQuestionInspection.class);
        inspectionClasses.add(NonBooleanMethodNameMayNotStartWithQuestionInspection.class);
        inspectionClasses.add(QuestionableNameInspection.class);
        inspectionClasses.add(ConfusingMainMethodInspection.class);
        inspectionClasses.add(UpperCaseFieldNameNotConstantInspection.class);
        inspectionClasses.add(DollarSignInNameInspection.class);
    }

    private void registerBugInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(IntegerDivisionInFloatingPointContextInspection.class);
        inspectionClasses.add(NonShortCircuitBooleanInspection.class);
        inspectionClasses.add(ComparisonOfShortAndCharInspection.class);
        inspectionClasses.add(AssignmentUsedAsConditionInspection.class);
        inspectionClasses.add(EmptyStatementBodyInspection.class);
        inspectionClasses.add(EmptyInitializerInspection.class);
        inspectionClasses.add(EqualsBetweenInconvertibleTypesInspection.class);
        inspectionClasses.add(CastToIncompatibleInterfaceInspection.class);
        inspectionClasses.add(CollectionAddedToSelfInspection.class);
        inspectionClasses.add(InstanceofIncompatibleInterfaceInspection.class);
        inspectionClasses.add(InstantiationOfUtilityClassInspection.class);
        inspectionClasses.add(BigDecimalEqualsInspection.class);
        inspectionClasses.add(CovariantCompareToInspection.class);
        inspectionClasses.add(CovariantEqualsInspection.class);
        inspectionClasses.add(FloatingPointEqualityInspection.class);
        inspectionClasses.add(MisspelledCompareToInspection.class);
        inspectionClasses.add(MisspelledHashcodeInspection.class);
        inspectionClasses.add(MisspelledEqualsInspection.class);
        inspectionClasses.add(MisspelledToStringInspection.class);
        inspectionClasses.add(FallthruInSwitchStatementInspection.class);
        inspectionClasses.add(SwitchStatementsWithoutDefaultInspection.class);
        inspectionClasses.add(DefaultNotLastCaseInSwitchInspection.class);
        inspectionClasses.add(ArrayEqualsInspection.class);
        inspectionClasses.add(ObjectEqualityInspection.class);
        inspectionClasses.add(ObjectEqualsNullInspection.class);
        inspectionClasses.add(StringEqualityInspection.class);
        inspectionClasses.add(IgnoreResultOfCallInspection.class);
        inspectionClasses.add(ResultOfObjectAllocationIgnoredInspection.class);
        inspectionClasses.add(ResultSetIndexZeroInspection.class);
        inspectionClasses.add(LoopStatementsThatDontLoopInspection.class);
        inspectionClasses.add(MalformedRegexInspection.class);
        inspectionClasses.add(MismatchedArrayReadWriteInspection.class);
        inspectionClasses.add(MismatchedCollectionQueryUpdateInspection.class);
        inspectionClasses.add(TextLabelInSwitchStatementInspection.class);
        inspectionClasses.add(UseOfPropertiesAsHashtableInspection.class);
        inspectionClasses.add(AssignmentToNullInspection.class);
        inspectionClasses.add(ConditionalExpressionWithIdenticalBranchesInspection.class);
        inspectionClasses.add(IfStatementWithIdenticalBranchesInspection.class);
        inspectionClasses.add(DuplicateConditionInspection.class);
        inspectionClasses.add(IteratorNextDoesNotThrowNoSuchElementExceptionInspection.class);
        inspectionClasses.add(ReturnNullInspection.class);
        inspectionClasses.add(ShiftOutOfRangeInspection.class);
        inspectionClasses.add(AssignmentToStaticFieldFromInstanceMethodInspection.class);
        inspectionClasses.add(StaticCallOnSubclassInspection.class);
        inspectionClasses.add(OctalAndDecimalIntegersMixedInspection.class);
        inspectionClasses.add(IncompatibleMaskInspection.class);
        inspectionClasses.add(ForLoopWithMissingComponentInspection.class);
        inspectionClasses.add(ForLoopThatDoesntUseLoopVariableInspection.class);
        inspectionClasses.add(InfiniteLoopStatementInspection.class);
        inspectionClasses.add(InfiniteRecursionInspection.class);
        inspectionClasses.add(SubtractionInCompareToInspection.class);
        inspectionClasses.add(EqualsUsesNonFinalVariableInspection.class);
        inspectionClasses.add(HashCodeUsesNonFinalVariableInspection.class);
        inspectionClasses.add(CompareToUsesNonFinalVariableInspection.class);
        inspectionClasses.add(EqualsWhichDoesntCheckParameterClassInspection.class);
        inspectionClasses.add(NullArgumentToVariableArgMethodInspection.class);
        inspectionClasses.add(EnumSwitchStatementWhichMissesCasesInspection.class);
    }

    private void registerAbstractionInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(DuplicateStringLiteralInspection.class);
        inspectionClasses.add(FeatureEnvyInspection.class);
        inspectionClasses.add(InstanceofChainInspection.class);
        inspectionClasses.add(LocalVariableOfConcreteClassInspection.class);
        inspectionClasses.add(RawUseOfParameterizedTypeInspection.class);
        inspectionClasses.add(InstanceVariableOfConcreteClassInspection.class);
        inspectionClasses.add(StaticVariableOfConcreteClassInspection.class);
        inspectionClasses.add(ParameterOfConcreteClassInspection.class);
        inspectionClasses.add(MethodReturnOfConcreteClassInspection.class);
        inspectionClasses.add(InstanceofInterfacesInspection.class);
        inspectionClasses.add(CastToConcreteClassInspection.class);
        inspectionClasses.add(OverlyStrongTypeCastInspection.class);
        inspectionClasses.add(DeclareCollectionAsInterfaceInspection.class);
        inspectionClasses.add(MagicNumberInspection.class);
        inspectionClasses.add(ClassReferencesSubclassInspection.class);
        inspectionClasses.add(SwitchStatementInspection.class);
        inspectionClasses.add(PublicMethodNotExposedInInterfaceInspection.class);
        inspectionClasses.add(InstanceofThisInspection.class);
    }

    private void registerClassLayoutInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(FinalClassInspection.class);
        inspectionClasses.add(EmptyClassInspection.class);
        inspectionClasses.add(AnonymousInnerClassInspection.class);
        inspectionClasses.add(LimitedScopeInnerClassInspection.class);
        inspectionClasses.add(FinalMethodInspection.class);
        inspectionClasses.add(ClassInitializerInspection.class);
        inspectionClasses.add(ClassMayBeInterfaceInspection.class);
        inspectionClasses.add(NonProtectedConstructorInAbstractClassInspection.class);
        inspectionClasses.add(ClassWithoutConstructorInspection.class);
        inspectionClasses.add(AbstractClassWithoutAbstractMethodsInspection.class);
        inspectionClasses.add(FinalMethodInFinalClassInspection.class);
        inspectionClasses.add(ProtectedMemberInFinalClassInspection.class);
        inspectionClasses.add(PublicConstructorInNonPublicClassInspection.class);
        inspectionClasses.add(UtilityClassWithPublicConstructorInspection.class);
        inspectionClasses.add(UtilityClassWithoutPrivateConstructorInspection.class);
        inspectionClasses.add(AbstractMethodOverridesConcreteMethodInspection.class);
        inspectionClasses.add(AbstractMethodOverridesAbstractMethodInspection.class);
        inspectionClasses.add(AbstractClassExtendsConcreteClassInspection.class);
        inspectionClasses.add(StaticNonFinalFieldInspection.class);
        inspectionClasses.add(ConstantDeclaredInAbstractClassInspection.class);
        inspectionClasses.add(ConstantDeclaredInInterfaceInspection.class);
        inspectionClasses.add(StaticInheritanceInspection.class);
        inspectionClasses.add(ClassInTopLevelPackageInspection.class);
        inspectionClasses.add(UtilityClassInspection.class);
        inspectionClasses.add(SingletonInspection.class);
        inspectionClasses.add(InnerClassOnInterfaceInspection.class);
        inspectionClasses.add(FinalPrivateMethodInspection.class);
        inspectionClasses.add(NoopMethodInAbstractClassInspection.class);
        inspectionClasses.add(FinalStaticMethodInspection.class);
        inspectionClasses.add(ClassWithoutNoArgConstructorInspection.class);
        inspectionClasses.add(MultipleTopLevelClassesInFileInspection.class);
        inspectionClasses.add(ClassNameDiffersFromFileNameInspection.class);
        inspectionClasses.add(MarkerInterfaceInspection.class);
        inspectionClasses.add(FieldHasSetterButNoGetterInspection.class);
        inspectionClasses.add(OverlyLargePrimitiveArrayInitializerInspection.class);
        inspectionClasses.add(AbstractClassNeverImplementedInspection.class);
        inspectionClasses.add(MissingDeprecatedAnnotationInspection.class);
        inspectionClasses.add(MissingOverrideAnnotationInspection.class);
        inspectionClasses.add(ExtendsAnnotationInspection.class);
    }

    private void registerCloneInspections(){
        m_inspectionClasses.add(CloneInNonCloneableClassInspection.class);
        m_inspectionClasses.add(CloneableImplementsCloneInspection.class);
        m_inspectionClasses.add(CloneCallsConstructorsInspection.class);
        m_inspectionClasses.add(CloneCallsSuperCloneInspection.class);
        m_inspectionClasses.add(CloneDeclaresCloneNotSupportedInspection.class);
    }

    private void registerVisibilityInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(FieldHidesSuperclassFieldInspection.class);
        inspectionClasses.add(InnerClassVariableHidesOuterClassVariableInspection.class);
        inspectionClasses.add(ParameterHidingMemberVariableInspection.class);
        inspectionClasses.add(LocalVariableHidingMemberVariableInspection.class);
        inspectionClasses.add(MethodOverridesPrivateMethodInspection.class);
        inspectionClasses.add(MethodOverridesStaticMethodInspection.class);
        inspectionClasses.add(MethodOverloadsParentMethodInspection.class);
        inspectionClasses.add(TypeParameterHidesVisibleTypeInspection.class);
    }

    private void registerEncapsulationInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(PublicFieldInspection.class);
        inspectionClasses.add(PackageVisibleFieldInspection.class);
        inspectionClasses.add(ProtectedFieldInspection.class);
        inspectionClasses.add(PublicInnerClassInspection.class);
        inspectionClasses.add(PackageVisibleInnerClassInspection.class);
        inspectionClasses.add(ProtectedInnerClassInspection.class);
        inspectionClasses.add(ReturnOfCollectionFieldInspection.class);
        inspectionClasses.add(ReturnOfDateFieldInspection.class);
        inspectionClasses.add(UseOfAnotherObjectsPrivateFieldInspection.class);
        inspectionClasses.add(AssignmentToCollectionFieldFromParameterInspection.class);
        inspectionClasses.add(AssignmentToDateFieldFromParameterInspection.class);
    }

    private void registerInitializerInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(StaticVariableInitializationInspection.class);
        inspectionClasses.add(InstanceVariableInitializationInspection.class);
        inspectionClasses.add(AbstractMethodCallInConstructorInspection.class);
        inspectionClasses.add(OverridableMethodCallInConstructorInspection.class);
        inspectionClasses.add(OverriddenMethodCallInConstructorInspection.class);
        inspectionClasses.add(ThisEscapedInConstructorInspection.class);
        inspectionClasses.add(StaticVariableUninitializedUseInspection.class);
        inspectionClasses.add(InstanceVariableUninitializedUseInspection.class);
    }

    private void registerConfusingInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(ClassEscapesItsScopeInspection.class);
        inspectionClasses.add(ConfusingFloatingPointLiteralInspection.class);
        inspectionClasses.add(OverlyComplexArithmeticExpressionInspection.class);
        inspectionClasses.add(OverlyComplexBooleanExpressionInspection.class);
        inspectionClasses.add(AssignmentToMethodParameterInspection.class);
        inspectionClasses.add(AssignmentToCatchBlockParameterInspection.class);
        inspectionClasses.add(AssignmentToForLoopParameterInspection.class);
        inspectionClasses.add(NestedAssignmentInspection.class);
        inspectionClasses.add(LabeledStatementInspection.class);
        inspectionClasses.add(BreakStatementInspection.class);
        inspectionClasses.add(BreakStatementWithLabelInspection.class);
        inspectionClasses.add(ContinueStatementInspection.class);
        inspectionClasses.add(ContinueStatementWithLabelInspection.class);
        inspectionClasses.add(ConditionalExpressionInspection.class);
        inspectionClasses.add(NestedConditionalExpressionInspection.class);
        inspectionClasses.add(LongLiteralsEndingWithLowercaseLInspection.class);
        inspectionClasses.add(IncrementDecrementUsedAsExpressionInspection.class);
        inspectionClasses.add(IfStatementWithTooManyBranchesInspection.class);
        inspectionClasses.add(SwitchStatementWithTooManyBranchesInspection.class);
        inspectionClasses.add(SwitchStatementWithTooFewBranchesInspection.class);
        inspectionClasses.add(SwitchStatementDensityInspection.class);
        inspectionClasses.add(NestedSwitchStatementInspection.class);
        inspectionClasses.add(ChainedMethodCallInspection.class);
        inspectionClasses.add(NestedMethodCallInspection.class);
        inspectionClasses.add(OctalLiteralInspection.class);
        inspectionClasses.add(ChainedEqualityInspection.class);
        inspectionClasses.add(ConfusingOctalEscapeInspection.class);
        inspectionClasses.add(MethodNamesDifferOnlyByCaseInspection.class);
        inspectionClasses.add(OverloadedMethodsWithSameNumberOfParametersInspection.class);
        inspectionClasses.add(ImplicitNumericConversionInspection.class);
        inspectionClasses.add(ImplicitCallToSuperInspection.class);
        inspectionClasses.add(RefusedBequestInspection.class);
        inspectionClasses.add(CastThatLosesPrecisionInspection.class);
        inspectionClasses.add(NegatedIfElseInspection.class);
        inspectionClasses.add(NegatedConditionalInspection.class);
        inspectionClasses.add(ConfusingElseInspection.class);
        inspectionClasses.add(SwitchStatementWithConfusingDeclarationInspection.class);
    }

    private void registerVerboseInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(UnnecessaryLabelOnBreakStatementInspection.class);
        inspectionClasses.add(UnnecessaryLabelOnContinueStatementInspection.class);
        inspectionClasses.add(PointlessBooleanExpressionInspection.class);
        inspectionClasses.add(ReplaceAssignmentWithOperatorAssignmentInspection.class);
        inspectionClasses.add(TrivialIfInspection.class);
        inspectionClasses.add(UnnecessaryConditionalExpressionInspection.class);
        inspectionClasses.add(UnnecessaryParenthesesInspection.class);
        inspectionClasses.add(UnnecessaryLocalVariableInspection.class);
        inspectionClasses.add(UnnecessaryThisInspection.class);
        inspectionClasses.add(UnnecessaryBlockStatementInspection.class);
        inspectionClasses.add(UnnecessaryInterfaceModifierInspection.class);
        inspectionClasses.add(UnnecessaryEnumModifierInspection.class);
        inspectionClasses.add(UnnecessaryReturnInspection.class);
        inspectionClasses.add(UnnecessaryContinueInspection.class);
        inspectionClasses.add(UnnecessarySemicolonInspection.class);
        inspectionClasses.add(UnnecessaryFullyQualifiedNameInspection.class);
        inspectionClasses.add(UnnecessaryQualifierForThisInspection.class);
        inspectionClasses.add(UnusedLabelInspection.class);
        inspectionClasses.add(RedundantFieldInitializationInspection.class);
        inspectionClasses.add(RedundantImplementsInspection.class);
        inspectionClasses.add(ExtendsObjectInspection.class);
        inspectionClasses.add(TypeParameterExtendsObjectInspection.class);
        inspectionClasses.add(PointlessArithmeticExpressionInspection.class);
        inspectionClasses.add(PointlessBitwiseExpressionInspection.class);
        inspectionClasses.add(UnnecessarySuperConstructorInspection.class);
        inspectionClasses.add(UnnecessaryConstructorInspection.class);
        inspectionClasses.add(ForLoopReplaceableByWhileInspection.class);
        inspectionClasses.add(UnnecessaryDefaultInspection.class);
        inspectionClasses.add(UnnecessaryBoxingInspection.class);
        inspectionClasses.add(UnnecessaryUnboxingInspection.class);
        inspectionClasses.add(UnnecessaryFinalOnParameterInspection.class);
        inspectionClasses.add(UnnecessaryFinalOnLocalVariableInspection.class);
        inspectionClasses.add(ForCanBeForeachInspection.class);
        inspectionClasses.add(WhileCanBeForeachInspection.class);
    }

    private void registerStyleInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(ReturnThisInspection.class);
        inspectionClasses.add(ConstantOnLHSOfComparisonInspection.class);
        inspectionClasses.add(ConstantOnRHSOfComparisonInspection.class);
        inspectionClasses.add(LiteralAsArgToStringEqualsInspection.class);
        inspectionClasses.add(MissortedModifiersInspection.class);
        inspectionClasses.add(CStyleArrayDeclarationInspection.class);
        inspectionClasses.add(MultipleDeclarationInspection.class);
        inspectionClasses.add(MultipleTypedDeclarationInspection.class);
        inspectionClasses.add(UnqualifiedStaticUsageInspection.class);
        inspectionClasses.add(UnnecessarilyQualifiedStaticUsageInspection.class);
    }

    private void registerExceptionInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(CatchGenericClassInspection.class);
        inspectionClasses.add(EmptyCatchBlockInspection.class);
        inspectionClasses.add(UnusedCatchParameterInspection.class);
        inspectionClasses.add(EmptyFinallyBlockInspection.class);
        inspectionClasses.add(EmptyTryBlockInspection.class);
        inspectionClasses.add(ThrowFromFinallyBlockInspection.class);
        inspectionClasses.add(ThrowCaughtLocallyInspection.class);
        inspectionClasses.add(ReturnFromFinallyBlockInspection.class);
        inspectionClasses.add(ContinueOrBreakFromFinallyBlockInspection.class);
        inspectionClasses.add(BadExceptionThrownInspection.class);
        inspectionClasses.add(BadExceptionDeclaredInspection.class);
        inspectionClasses.add(BadExceptionCaughtInspection.class);
        inspectionClasses.add(TooBroadCatchInspection.class);
        inspectionClasses.add(CheckedExceptionClassInspection.class);
        inspectionClasses.add(UncheckedExceptionClassInspection.class);
        inspectionClasses.add(ThreadDeathRethrownInspection.class);
        inspectionClasses.add(ErrorRethrownInspection.class);
        inspectionClasses.add(NestedTryStatementInspection.class);
        inspectionClasses.add(ExceptionFromCatchWhichDoesntWrapInspection.class);
        inspectionClasses.add(InstanceofCatchParameterInspection.class);
    }

    private void registerFinalizationInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(FinalizeInspection.class);
        inspectionClasses.add(FinalizeNotProtectedInspection.class);
        inspectionClasses.add(FinalizeCallsSuperFinalizeInspection.class);
        inspectionClasses.add(NoExplicitFinalizeCallsInspection.class);
    }

    private void registerSerializationInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(SerializableHasSerializationMethodsInspection.class);
        inspectionClasses.add(SerializableHasSerialVersionUIDFieldInspection.class);
        inspectionClasses.add(ReadObjectInitializationInspection.class);
        inspectionClasses.add(ReadObjectAndWriteObjectPrivateInspection.class);
        inspectionClasses.add(SerialVersionUIDNotStaticFinalInspection.class);
        inspectionClasses.add(SerialPersistentFieldsWithWrongSignatureInspection.class);
        inspectionClasses.add(ReadResolveAndWriteReplaceProtectedInspection.class);
        inspectionClasses.add(TransientFieldInNonSerializableClassInspection.class);
        inspectionClasses.add(SerializableWithUnconstructableAncestorInspection.class);
        inspectionClasses.add(NonSerializableWithSerializationMethodsInspection.class);
        inspectionClasses.add(ExternalizableWithSerializationMethodsInspection.class);
        inspectionClasses.add(NonSerializableWithSerialVersionUIDFieldInspection.class);
        inspectionClasses.add(SerializableInnerClassHasSerialVersionUIDFieldInspection.class);
        inspectionClasses.add(SerializableInnerClassWithNonSerializableOuterClassInspection.class);
    }

    private void registerThreadingInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(DoubleCheckedLockingInspection.class);
        inspectionClasses.add(BusyWaitInspection.class);
        inspectionClasses.add(ArithmeticOnVolatileFieldInspection.class);
        inspectionClasses.add(CallToNativeMethodWhileLockedInspection.class);
        inspectionClasses.add(ObjectNotifyInspection.class);
        inspectionClasses.add(ThreadWithDefaultRunMethodInspection.class);
        inspectionClasses.add(NakedNotifyInspection.class);
        inspectionClasses.add(UnconditionalWaitInspection.class);
        inspectionClasses.add(SystemRunFinalizersOnExitInspection.class);
        inspectionClasses.add(ThreadYieldInspection.class);
        inspectionClasses.add(ThreadStopSuspendResumeInspection.class);
        inspectionClasses.add(WhileLoopSpinsOnFieldInspection.class);
        inspectionClasses.add(WaitNotInLoopInspection.class);
        inspectionClasses.add(VolatileLongOrDoubleFieldInspection.class);
        inspectionClasses.add(VolatileArrayFieldInspection.class);
        inspectionClasses.add(WaitNotInSynchronizedContextInspection.class);
        inspectionClasses.add(WaitWhileHoldingTwoLocksInspection.class);
        inspectionClasses.add(NotifyNotInSynchronizedContextInspection.class);
        inspectionClasses.add(ThreadRunInspection.class);
        inspectionClasses.add(ThreadStartInConstructionInspection.class);
        inspectionClasses.add(SynchronizedMethodInspection.class);
        inspectionClasses.add(SynchronizeOnLockInspection.class);
        inspectionClasses.add(SynchronizeOnNonFinalFieldInspection.class);
        inspectionClasses.add(SynchronizeOnThisInspection.class);
        inspectionClasses.add(NestedSynchronizedStatementInspection.class);
        inspectionClasses.add(EmptySynchronizedStatementInspection.class);
        inspectionClasses.add(NonSynchronizedMethodOverridesSynchronizedMethodInspection.class);
        inspectionClasses.add(PublicFieldAccessedInSynchronizedContextInspection.class);
        inspectionClasses.add(FieldAccessedSynchronizedAndUnsynchronizedInspection.class);
    }

    private void registerMethodMetricsInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(ThreeNegationsPerMethodInspection.class);
        inspectionClasses.add(MethodWithMultipleLoopsInspection.class);
        inspectionClasses.add(MultipleReturnPointsPerMethodInspection.class);
        inspectionClasses.add(ThrownExceptionsPerMethodInspection.class);
        inspectionClasses.add(ParametersPerMethodInspection.class);
        inspectionClasses.add(CyclomaticComplexityInspection.class);
        inspectionClasses.add(NestingDepthInspection.class);
        inspectionClasses.add(NonCommentSourceStatementsInspection.class);
        inspectionClasses.add(MethodCouplingInspection.class);
    }

    private void registerClassMetricsInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(AnonymousClassComplexityInspection.class);
        inspectionClasses.add(AnonymousClassMethodCountInspection.class);
        inspectionClasses.add(ClassComplexityInspection.class);
        inspectionClasses.add(ClassInheritanceDepthInspection.class);
        inspectionClasses.add(ClassNestingDepthInspection.class);
        inspectionClasses.add(ClassCouplingInspection.class);
        inspectionClasses.add(ConstructorCountInspection.class);
        inspectionClasses.add(MethodCountInspection.class);
        inspectionClasses.add(FieldCountInspection.class);
    }

    private void registerPortabilityInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(RuntimeExecInspection.class);
        inspectionClasses.add(SystemExitInspection.class);
        inspectionClasses.add(SystemGetenvInspection.class);
        inspectionClasses.add(HardcodedLineSeparatorsInspection.class);
        inspectionClasses.add(HardcodedFileSeparatorsInspection.class);
        inspectionClasses.add(NativeMethodsInspection.class);
        inspectionClasses.add(AssertAsNameInspection.class);
        inspectionClasses.add(EnumAsNameInspection.class);
        inspectionClasses.add(AssertStatementInspection.class);
        inspectionClasses.add(AutoBoxingInspection.class);
        inspectionClasses.add(AutoUnboxingInspection.class);
        inspectionClasses.add(VarargParameterInspection.class);
        inspectionClasses.add(ForeachStatementInspection.class);
        inspectionClasses.add(EnumClassInspection.class);
        inspectionClasses.add(AnnotationClassInspection.class);
        inspectionClasses.add(AnnotationInspection.class);
        inspectionClasses.add(UseOfSunClassesInspection.class);
        inspectionClasses.add(UseOfAWTPeerClassInspection.class);
        inspectionClasses.add(UseOfJDBCDriverClassInspection.class);
    }

    private void registerInternationalInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(StringLiteralInspection.class);
        inspectionClasses.add(CharacterComparisonInspection.class);
        inspectionClasses.add(MagicCharacterInspection.class);
        inspectionClasses.add(NumericToStringInspection.class);
        inspectionClasses.add(DateToStringInspection.class);
        inspectionClasses.add(TimeToStringInspection.class);
        inspectionClasses.add(StringCompareToInspection.class);
        inspectionClasses.add(StringEqualsIgnoreCaseInspection.class);
        inspectionClasses.add(StringEqualsInspection.class);
        inspectionClasses.add(StringConcatenationInspection.class);
        inspectionClasses.add(StringTokenizerInspection.class);
        inspectionClasses.add(StringToUpperWithoutLocaleInspection.class);
        inspectionClasses.add(SimpleDateFormatWithoutLocaleInspection.class);
    }

    private void registerPerformanceInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(PrivateMemberAccessBetweenOuterAndInnerClassInspection.class);
        inspectionClasses.add(ObjectAllocationInLoopInspection.class);
        inspectionClasses.add(InstantiatingObjectToGetClassObjectInspection.class);
        inspectionClasses.add(UnnecessaryTemporaryOnConversionToStringInspection.class);
        inspectionClasses.add(UnnecessaryTemporaryOnConversionFromStringInspection.class);
        inspectionClasses.add(FieldMayBeStaticInspection.class);
        inspectionClasses.add(MethodMayBeStaticInspection.class);
        inspectionClasses.add(InnerClassMayBeStaticInspection.class);
        inspectionClasses.add(AnonymousInnerClassMayBeStaticInspection.class);
        inspectionClasses.add(StringBufferMustHaveInitialCapacityInspection.class);
        inspectionClasses.add(StringBufferReplaceableByStringBuilderInspection.class);
        inspectionClasses.add(StringBufferReplaceableByStringInspection.class);
        inspectionClasses.add(StringReplaceableByStringBufferInspection.class);
        inspectionClasses.add(CollectionsMustHaveInitialCapacityInspection.class);
        inspectionClasses.add(StringBufferFieldInspection.class);
        inspectionClasses.add(StringConcatenationInLoopsInspection.class);
        inspectionClasses.add(MultiplyOrDivideByPowerOfTwoInspection.class);
        inspectionClasses.add(LengthOneStringsInConcatenationInspection.class);
        inspectionClasses.add(BooleanConstructorInspection.class);
        inspectionClasses.add(StringToStringInspection.class);
        inspectionClasses.add(StringConstructorInspection.class);
        inspectionClasses.add(StringBufferToStringInConcatenationInspection.class);
        inspectionClasses.add(TailRecursionInspection.class);
        inspectionClasses.add(TrivialStringConcatenationInspection.class);
        inspectionClasses.add(SystemGCInspection.class);
        inspectionClasses.add(SingleCharacterStartsWithInspection.class);
        inspectionClasses.add(StringEqualsEmptyStringInspection.class);
        inspectionClasses.add(RandomDoubleForRandomIntegerInspection.class);
        inspectionClasses.add(FieldRepeatedlyAccessedInspection.class);
        inspectionClasses.add(ManualArrayCopyInspection.class);
        inspectionClasses.add(JavaLangReflectInspection.class);
        inspectionClasses.add(StaticCollectionInspection.class);
        inspectionClasses.add(ZeroLengthArrayInitializationInspection.class);
        inspectionClasses.add(CallToSimpleGetterInClassInspection.class);
        inspectionClasses.add(CallToSimpleSetterInClassInspection.class);
    }

    private void registerJ2MEInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(AbstractClassWithOnlyOneDirectInheritorInspection.class);
        inspectionClasses.add(InterfaceWithOnlyOneDirectInheritorInspection.class);
        inspectionClasses.add(CheckForOutOfMemoryOnLargeArrayAllocationInspection.class);
        inspectionClasses.add(OverlyLargePrimitiveArrayInitializerInspection.class);
        inspectionClasses.add(RecordStoreResourceInspection.class);
        inspectionClasses.add(ConnectionResourceInspection.class);
        inspectionClasses.add(MethodCallInLoopConditionInspection.class);
        inspectionClasses.add(ArrayLengthInLoopConditionInspection.class);
    }

    private void registerMaturityInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(SystemOutErrInspection.class);
        inspectionClasses.add(ThrowablePrintStackTraceInspection.class);
        inspectionClasses.add(TodoCommentInspection.class);
        inspectionClasses.add(ThreadDumpStackInspection.class);
        inspectionClasses.add(ClassWithoutToStringInspection.class);
        inspectionClasses.add(ObsoleteCollectionInspection.class);
    }

    private void registerJUnitInspections(){
        final List<Class> inspectionClasses = m_inspectionClasses;
        inspectionClasses.add(AssertsWithoutMessagesInspection.class);
        inspectionClasses.add(TestCaseWithConstructorInspection.class);
        inspectionClasses.add(MisspelledSetUpInspection.class);
        inspectionClasses.add(MisorderedAssertEqualsParametersInspection.class);
        inspectionClasses.add(MisspelledTearDownInspection.class);
        inspectionClasses.add(StaticSuiteInspection.class);
        inspectionClasses.add(SetupCallsSuperSetupInspection.class);
        inspectionClasses.add(TeardownCallsSuperTeardownInspection.class);
        inspectionClasses.add(SetupIsPublicVoidNoArgInspection.class);
        inspectionClasses.add(SimplifiableJUnitAssertionInspection.class);
        inspectionClasses.add(TeardownIsPublicVoidNoArgInspection.class);
        inspectionClasses.add(TestMethodIsPublicVoidNoArgInspection.class);
        inspectionClasses.add(TestMethodWithoutAssertionInspection.class);
        inspectionClasses.add(TestCaseWithNoTestMethodsInspection.class);
        inspectionClasses.add(TestCaseInProductCodeInspection.class);
        inspectionClasses.add(UnconstructableTestCaseInspection.class);
    }

    public void disposeComponent(){
    }

    public boolean isTelemetryEnabled()
    {
        return TELEMETRY_ENABLED;
    }

    public InspectionGadgetsTelemetry getTelemetry(){
        return telemetry;
    }
}
