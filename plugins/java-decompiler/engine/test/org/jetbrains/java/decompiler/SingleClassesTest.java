// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.ClassFormatException;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jetbrains.java.decompiler.DecompilerTestFixture.assertFilesEqual;
import static org.junit.Assert.assertTrue;

public class SingleClassesTest {
  private DecompilerTestFixture fixture;

  /*
   * Set individual test duration time limit to 60 seconds.
   * This will help us to test bugs hanging decompiler.
   */
  @Rule
  public Timeout globalTimeout = Timeout.seconds(60);

  @Before
  public void setUp() throws IOException {
    fixture = new DecompilerTestFixture();
    fixture.setUp(Map.of(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1",
                         IFernflowerPreferences.DUMP_ORIGINAL_LINES, "1",
                         IFernflowerPreferences.IGNORE_INVALID_BYTECODE, "1",
                         IFernflowerPreferences.VERIFY_ANONYMOUS_CLASSES, "1"));
  }

  @After
  public void tearDown() throws IOException {
    fixture.tearDown();
    fixture = null;
  }

  @Test public void testPrimitiveNarrowing() { doTest("pkg/TestPrimitiveNarrowing"); }
  @Test public void testClassFields() { doTest("pkg/TestClassFields"); }
  @Test public void testInterfaceFields() { doTest("pkg/TestInterfaceFields"); }
  @Test public void testClassLambda() { doTest("pkg/TestClassLambda"); }
  @Test public void testClassLoop() { doTest("pkg/TestClassLoop"); }
  @Test public void testClassSwitch() { doTest("pkg/TestClassSwitch"); }
  @Test public void testClassTypes() { doTest("pkg/TestClassTypes"); }
  @Test public void testClassVar() { doTest("pkg/TestClassVar"); }
  @Test public void testClassNestedInitializer() { doTest("pkg/TestClassNestedInitializer"); }
  @Test public void testClassCast() { doTest("pkg/TestClassCast"); }
  @Test public void testDeprecations() { doTest("pkg/TestDeprecations"); }
  @Test public void testExtendsList() { doTest("pkg/TestExtendsList"); }
  @Test public void testMethodParameters() { doTest("pkg/TestMethodParameters"); }
  @Test public void testMethodParametersAttr() { doTest("pkg/TestMethodParametersAttr"); }
  @Test public void testCodeConstructs() { doTest("pkg/TestCodeConstructs"); }
  @Test public void testConstantsAsIs() { doTest("pkg/TestConstantsAsIs"); }
  @Test public void testConstants() {
    DecompilerContext.setProperty(IFernflowerPreferences.LITERALS_AS_IS, "0");
    doTest("pkg/TestConstants");
  }
  @Test public void testInteger() {
    DecompilerContext.setProperty(IFernflowerPreferences.LITERALS_AS_IS, "0");
    doTest("java/lang/Integer");
  }
  @Test public void testEnum() { doTest("pkg/TestEnum"); }
  @Test public void testDebugSymbols() { doTest("pkg/TestDebugSymbols"); }
  @Test public void testInvalidMethodSignature() { doTest("InvalidMethodSignature"); }
  @Test public void testAnonymousClassConstructor() { doTest("pkg/TestAnonymousClassConstructor"); }
  @Test public void testInnerClassConstructor() { doTest("pkg/TestInnerClassConstructor"); }
  @Test public void testInnerClassConstructor11() { doTest("v11/TestInnerClassConstructor"); }
  @Test public void testTryCatchFinally() { doTest("pkg/TestTryCatchFinally"); }
  @Test public void testTryCatchFinallyJsrRet() { doTest("pkg/TestTryCatchFinallyJsrRet"); }
  @Test public void testAmbiguousCall() { doTest("pkg/TestAmbiguousCall"); }
  @Test public void testAmbiguousCallWithDebugInfo() { doTest("pkg/TestAmbiguousCallWithDebugInfo"); }
  @Test public void testSimpleBytecodeMapping() { doTest("pkg/TestClassSimpleBytecodeMapping"); }
  @Test public void testSynchronizedMapping() { doTest("pkg/TestSynchronizedMapping"); }
  @Test public void testAbstractMethods() { doTest("pkg/TestAbstractMethods"); }
  @Test public void testLocalClass() { doTest("pkg/TestLocalClass"); }
  @Test public void testAnonymousClass() { doTest("pkg/TestAnonymousClass"); }
  @Test public void testThrowException() { doTest("pkg/TestThrowException"); }
  @Test public void testInnerLocal() { doTest("pkg/TestInnerLocal"); }
  @Test public void testInnerSignature() { doTest("pkg/TestInnerSignature"); }
  @Test public void testAnonymousSignature() { doTest("pkg/TestAnonymousSignature"); }
  @Test public void testLocalsSignature() { doTest("pkg/TestLocalsSignature"); }
  @Test public void testParameterizedTypes() { doTest("pkg/TestParameterizedTypes"); }
  @Test public void testShadowing() { doTest("pkg/TestShadowing", "pkg/Shadow", "ext/Shadow", "pkg/TestShadowingSuperClass"); }
  @Test public void testStringConcat() { doTest("pkg/TestStringConcat"); }
  @Test public void testJava9StringConcat() { doTest("java9/TestJava9StringConcat"); }
  @Test public void testJava9ModuleInfo() { doTest("java9/module-info"); }
  @Test public void testJava9PrivateInterfaceMethod() { doTest("java9/TestJava9PrivateInterfaceMethod"); }
  @Test public void testJava11StringConcat() { doTest("java11/TestJava11StringConcat"); }
  @Test public void testJava11StringConcatEmptyAffix() { doTest("java11/TestJava11StringConcatEmptyAffix"); }
  @Test public void testJava11StringConcatSpecialChars() { doTest("java11/TestJava11StringConcatSpecialChars"); }
  @Test public void testMethodReferenceSameName() { doTest("pkg/TestMethodReferenceSameName"); }
  @Test public void testMethodReferenceLetterClass() { doTest("pkg/TestMethodReferenceLetterClass"); }
  @Test public void testConstructorReference() { doTest("pkg/TestConstructorReference"); }
  @Test public void testMemberAnnotations() { doTest("pkg/TestMemberAnnotations"); }
  @Test public void testMoreAnnotations() { doTest("pkg/MoreAnnotations"); }
  @Test public void testStaticNameClash() { doTest("pkg/TestStaticNameClash"); }
  @Test public void testExtendingSubclass() { doTest("pkg/TestExtendingSubclass"); }
  @Test public void testSyntheticAccess() { doTest("pkg/TestSyntheticAccess"); }
  @Test public void testIllegalVarName() { doTest("pkg/TestIllegalVarName"); }
  @Test public void testIffSimplification() { doTest("pkg/TestIffSimplification"); }
  @Test public void testKotlinConstructor() { doTest("pkg/TestKotlinConstructorKt"); }
  @Test public void testAsserts() { doTest("pkg/TestAsserts"); }
  @Test public void testLocalsNames() { doTest("pkg/TestLocalsNames"); }
  @Test public void testAnonymousParamNames() { doTest("pkg/TestAnonymousParamNames"); }
  @Test public void testAnonymousParams() { doTest("pkg/TestAnonymousParams"); }
  @Test public void testAccessReplace() { doTest("pkg/TestAccessReplace"); }
  @Test public void testStringLiterals() { doTest("pkg/TestStringLiterals"); }
  @Test public void testPrimitives() { doTest("pkg/TestPrimitives"); }
  @Test public void testClashName() { doTest("pkg/TestClashName", "pkg/SharedName1",
          "pkg/SharedName2", "pkg/SharedName3", "pkg/SharedName4", "pkg/NonSharedName",
          "pkg/TestClashNameParent", "ext/TestClashNameParent","pkg/TestClashNameIface", "ext/TestClashNameIface"); }
  @Test public void testSwitchOnEnum() { doTest("pkg/TestSwitchOnEnum");}
  @Test public void testSwitchOnEnumEclipse() { doTest("pkg/TestSwitchOnEnumEclipse"); }
  @Test public void testVarArgCalls() { doTest("pkg/TestVarArgCalls"); }
  @Test public void testLambdaParams() { doTest("pkg/TestLambdaParams"); }
  @Test public void testInterfaceMethods() { doTest("pkg/TestInterfaceMethods"); }
  @Test public void testConstType() { doTest("pkg/TestConstType"); }
  @Test public void testPop2OneDoublePop2() { doTest("pkg/TestPop2OneDoublePop2"); }
  @Test public void testPop2OneLongPop2() { doTest("pkg/TestPop2OneLongPop2"); }
  @Test public void testPop2TwoIntPop2() { doTest("pkg/TestPop2TwoIntPop2"); }
  @Test public void testPop2TwoIntTwoPop() { doTest("pkg/TestPop2TwoIntTwoPop"); }
  @Test public void testSuperInner() { doTest("pkg/TestSuperInner", "pkg/TestSuperInnerBase"); }
  @Test public void testMissingConstructorCallGood() { doTest("pkg/TestMissingConstructorCallGood"); }
  @Test public void testMissingConstructorCallBad() { doTest("pkg/TestMissingConstructorCallBad"); }
  @Test public void testEmptyBlocks() { doTest("pkg/TestEmptyBlocks"); }
  @Test public void testInvertedFloatComparison() { doTest("pkg/TestInvertedFloatComparison"); }
  @Test public void testPrivateEmptyConstructor() { doTest("pkg/TestPrivateEmptyConstructor"); }
  @Test public void testSynchronizedUnprotected() { doTest("pkg/TestSynchronizedUnprotected"); }
  @Test public void testInterfaceSuper() { doTest("pkg/TestInterfaceSuper"); }
  @Test public void testFieldSingleAccess() { doTest("pkg/TestFieldSingleAccess"); }
  @Test public void testPackageInfo() { doTest("pkg/package-info"); }
  @Test public void testIntVarMerge() { doTest("pkg/TestIntVarMerge"); }
  @Test public void testSwitchOnStringsJavac() { doTest("pkg/TestSwitchOnStringsJavac"); }
  @Test public void testSwitchOnStringsEcj() { doTest("pkg/TestSwitchOnStringsEcj"); }
  @Test public void testSwitchRules() { doTest("pkg/TestSwitchRules"); }
  @Test public void testSwitchSimpleReferencesJavac() { doTest("pkg/TestSwitchSimpleReferencesJavac"); }
  @Test public void testSwitchClassReferencesJavac() { doTest("pkg/TestSwitchClassReferencesJavac"); }
  @Test public void testSwitchClassReferencesEcj() { doTest("pkg/TestSwitchClassReferencesEcj"); }
  @Test public void testSwitchClassReferencesFastExitJavac() { doTest("pkg/TestSwitchClassReferencesFastExitJavac"); }
  @Test public void testSwitchClassReferencesFastExitEcj() { doTest("pkg/TestSwitchClassReferencesFastExitEcj"); }
  @Test public void testSwitchGuardedJavac() { doTest("pkg/TestSwitchGuardedJavac"); }
  @Test public void testSwitchGuarded2Javac() { doTest("pkg/TestSwitchGuarded2Javac"); }
  @Test public void testSwitchGuardedEcj() { doTest("pkg/TestSwitchGuardedEcj"); }

  //ecj doesn't support here, because it produces code with unnecessary assignments,
  //which can confuse decompiler with ordinary ones
  @Test public void testSimpleInstanceOfRecordPatternJavac() { doTest("pkg/TestSimpleInstanceOfRecordPatternJavac"); }
  @Test public void testComplexInstanceOfRecordPatternJavac() { doTest("pkg/TestComplexInstanceOfRecordPatternJavac"); }
  @Test public void testSwitchWithDeconstructionsWithoutNestedJavac() { doTest("pkg/TestSwitchWithDeconstructionsWithoutNestedJavac"); }
  @Test public void testSwitchNestedDeconstructionJavac() { doTest("pkg/TestSwitchNestedDeconstructionsJavac"); }

  // TODO: fix all below
  //@Test public void testUnionType() { doTest("pkg/TestUnionType"); }
  //@Test public void testInnerClassConstructor2() { doTest("pkg/TestInner2"); }
  //@Test public void testInUse() { doTest("pkg/TestInUse"); }
  @Test public void testGroovyClass() { doTest("pkg/TestGroovyClass"); }
  @Test public void testGroovyTrait() { doTest("pkg/TestGroovyTrait"); }
  @Test public void testPrivateClasses() { doTest("pkg/PrivateClasses"); }
  @Test public void testSuspendLambda() { doTest("pkg/TestSuspendLambdaKt"); }
  @Test public void testNamedSuspendFun2Kt() { doTest("pkg/TestNamedSuspendFun2Kt"); }
  @Test public void testGenericArgs() { doTest("pkg/TestGenericArgs"); }
  @Test public void testRecordEmpty() { doTest("records/TestRecordEmpty"); }
  @Test public void testRecordSimple() { doTest("records/TestRecordSimple"); }
  @Test public void testRecordVararg() { doTest("records/TestRecordVararg"); }
  @Test public void testRecordGenericVararg() { doTest("records/TestRecordGenericVararg"); }
  @Test public void testRecordAnno() { doTest("records/TestRecordAnno"); }
  @Test public void testRootWithClassInner() { doTest("sealed/RootWithClassInner"); }
  @Test public void testRootWithInterfaceInner() { doTest("sealed/RootWithInterfaceInner"); }
  @Test public void testRootWithClassOuter() { doTest("sealed/RootWithClassOuter",
    "sealed/ClassExtends", "sealed/ClassNonSealed", "sealed/ClassNonSealedExtendsImplements");
  }
  @Test public void testRootWithClassOuterUnresolvable() { doTest("sealed/RootWithClassOuter"); }
  @Test public void testRootWithInterfaceOuter() { doTest("sealed/RootWithInterfaceOuter",
    "sealed/ClassImplements", "sealed/InterfaceNonSealed", "sealed/ClassNonSealedExtendsImplements");
  }
  @Test public void testClassNonSealed() { doTest("sealed/ClassNonSealed",
    "sealed/RootWithClassOuter", "sealed/ClassExtends", "sealed/ClassNonSealedExtendsImplements");
  }
  @Test public void testClassNonSealedExtendsImplements() { doTest("sealed/ClassNonSealedExtendsImplements",
    "sealed/RootWithClassOuter", "sealed/ClassExtends", "sealed/ClassNonSealed");
  }
  @Test public void testInterfaceNonSealed() { doTest("sealed/InterfaceNonSealed",
    "sealed/RootWithInterfaceOuter", "sealed/ClassImplements", "sealed/ClassNonSealedExtendsImplements");
  }
  @Test public void testRootWithModule() { doTest("sealed/foo/RootWithModule", "sealed/bar/BarClassExtends");}
  @Test public void testRootWithInterfaceInnerAndOuter() { doTest("sealed/RootWithInterfaceInnerAndOuter", "sealed/ClassNonSealed");}
  @Test public void testEnumWithOverride() { doTest("sealed/EnumWithOverride");}
  @Test public void testArrayTypeAnnotations() { doTest("typeAnnotations/ArrayTypeAnnotations",
    "typeAnnotations/A", "typeAnnotations/B", "typeAnnotations/C", "typeAnnotations/D");
  }
  @Test public void testGenericTypeAnnotations() { doTest("typeAnnotations/GenericTypeAnnotations",
    "typeAnnotations/A", "typeAnnotations/B", "typeAnnotations/C", "typeAnnotations/D", "typeAnnotations/E");
  }
  @Test public void testGenericArrayTypeAnnotations() {doTest("typeAnnotations/GenericArrayTypeAnnotations",
      "typeAnnotations/A", "typeAnnotations/B", "typeAnnotations/C", "typeAnnotations/D", "typeAnnotations/E", "typeAnnotations/F");
  }
  @Test public void testNestedTypeAnnotations() {doTest("typeAnnotations/NestedTypeAnnotations",
    "typeAnnotations/A", "typeAnnotations/B", "typeAnnotations/C", "typeAnnotations/D", "typeAnnotations/E",
    "typeAnnotations/F", "typeAnnotations/Z", "typeAnnotations/P", "typeAnnotations/S", "typeAnnotations/T");
  }
  @Test public void testArrayNestedTypeAnnotations() {doTest("typeAnnotations/ArrayNestedTypeAnnotations",
    "typeAnnotations/A", "typeAnnotations/B", "typeAnnotations/C", "typeAnnotations/D", "typeAnnotations/Z");
  }
  @Test public void testGenericNestedTypeAnnotations() {doTest("typeAnnotations/GenericNestedTypeAnnotations",
    "typeAnnotations/A", "typeAnnotations/B", "typeAnnotations/C", "typeAnnotations/D", "typeAnnotations/E",
    "typeAnnotations/V");
  }
  @Test public void testGenericArrayNestedTypeAnnotations() {doTest("typeAnnotations/GenericArrayNestedTypeAnnotations",
    "typeAnnotations/A", "typeAnnotations/B", "typeAnnotations/C", "typeAnnotations/D", "typeAnnotations/E",
    "typeAnnotations/F", "typeAnnotations/V");
  }
  @Test public void testClassSuperTypeAnnotations() {doTest("typeAnnotations/ClassSuperTypeAnnotations",
    "typeAnnotations/A", "typeAnnotations/B", "typeAnnotations/F");
  }
  @Test public void testInterfaceSuperTypeAnnotations() {doTest("typeAnnotations/InterfaceSuperTypeAnnotations",
    "typeAnnotations/A", "typeAnnotations/B", "typeAnnotations/F");
  }
  @Test public void testMemberDeclarationTypeAnnotations() {doTest("typeAnnotations/MemberDeclarationTypeAnnotations",
    "typeAnnotations/A", "typeAnnotations/B", "typeAnnotations/C", "typeAnnotations/D", "typeAnnotations/E",
                                                                   "typeAnnotations/K", "typeAnnotations/L");
  }
  @Test public void testNestedType() { doTest("pkg/NestedType"); }
  @Test public void testInheritanceChainCycle() { doTest("pkg/TestInheritanceChainCycle"); }
  @Test public void testDynamicConstantPoolEntry() { doTest("java11/TestDynamicConstantPoolEntry"); }
  @Test public void testInstanceofWithPattern() {
    doTest("patterns/TestInstanceofWithPattern");
  }
  @Test public void testInstanceofVarNotSupported() {
    // the bytecode version of this test data doesn't support patterns in `instanceof`, so no modifications regarding that are applied
    doTest("patterns/TestInstanceofPatternNotSupported");
  }

  @Test(expected = ClassFormatException.class)
  public void testUnsupportedConstantPoolEntry() { doTest("java11/TestUnsupportedConstantPoolEntry"); }

  private void doTest(String testFile, String... companionFiles) {
    var decompiler = fixture.getDecompiler();

    var classFile = fixture.getTestDataDir().resolve("classes/" + testFile + ".class");
    assertThat(classFile).isRegularFile();
    for (var file : collectClasses(classFile)) {
      decompiler.addSource(file.toFile());
    }

    for (String companionFile : companionFiles) {
      var companionClassFile = fixture.getTestDataDir().resolve("classes/" + companionFile + ".class");
      assertThat(companionClassFile).isRegularFile();
      for (var file : collectClasses(companionClassFile)) {
        decompiler.addSource(file.toFile());
      }
    }

    decompiler.decompileContext();

    var decompiledFile = fixture.getTargetDir().resolve(classFile.getFileName().toString().replace(".class", ".java"));
    assertThat(decompiledFile).isRegularFile();
    assertTrue(Files.isRegularFile(decompiledFile));
    var referenceFile = fixture.getTestDataDir().resolve("results/" + classFile.getFileName().toString().replace(".class", ".dec"));
    assertThat(referenceFile).isRegularFile();
    assertFilesEqual(referenceFile, decompiledFile);
  }

  static List<Path> collectClasses(Path classFile) {
    var files = new ArrayList<Path>();
    files.add(classFile);

    var parent = classFile.getParent();
    if (parent != null) {
      var glob = classFile.getFileName().toString().replace(".class", "$*.class");
      try (DirectoryStream<Path> inner = Files.newDirectoryStream(parent, glob)) {
        inner.forEach(files::add);
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    return files;
  }
}
