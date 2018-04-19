import pkg.<warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning>;
import static pkg.<warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning>.NON_EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS;
import static pkg.<warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning>.staticNonExperimentalMethodInExperimentalClass;
import static pkg.<warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning>.<warning descr="'EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS' is unstable">EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS</warning>;
import static pkg.<warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning>.<warning descr="'staticExperimentalMethodInExperimentalClass' is unstable">staticExperimentalMethodInExperimentalClass</warning>;

import pkg.NonExperimentalClass;
import static pkg.NonExperimentalClass.NON_EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS;
import static pkg.NonExperimentalClass.staticNonExperimentalMethodInNonExperimentalClass;
import static pkg.NonExperimentalClass.<warning descr="'EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS' is unstable">EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS</warning>;
import static pkg.NonExperimentalClass.<warning descr="'staticExperimentalMethodInNonExperimentalClass' is unstable">staticExperimentalMethodInNonExperimentalClass</warning>;

import pkg.<warning descr="'ExperimentalEnum' is unstable">ExperimentalEnum</warning>;
import pkg.NonExperimentalEnum;
import static pkg.<warning descr="'ExperimentalEnum' is unstable">ExperimentalEnum</warning>.NON_EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM;
import static pkg.<warning descr="'ExperimentalEnum' is unstable">ExperimentalEnum</warning>.<warning descr="'EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM' is unstable">EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM</warning>;
import static pkg.NonExperimentalEnum.NON_EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM;
import static pkg.NonExperimentalEnum.<warning descr="'EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM' is unstable">EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM</warning>;

import pkg.<warning descr="'ExperimentalAnnotation' is unstable">ExperimentalAnnotation</warning>;
import pkg.NonExperimentalAnnotation;

import <warning descr="'unstablePkg' is unstable">unstablePkg</warning>.ClassInUnstablePkg;

public class UnstableElementsTest {
  public void test() {
    String s = <warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning>.NON_EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS;
    <warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning>.staticNonExperimentalMethodInExperimentalClass();
    <warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning> experimentalClassInstanceViaNonExperimentalConstructor = new ExperimentalClass();
    s = experimentalClassInstanceViaNonExperimentalConstructor.nonExperimentalFieldInExperimentalClass;
    experimentalClassInstanceViaNonExperimentalConstructor.nonExperimentalMethodInExperimentalClass();
    s = NON_EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS;
    staticNonExperimentalMethodInExperimentalClass();

    s = <warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning>.<warning descr="'EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS' is unstable">EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS</warning>;
    <warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning>.<warning descr="'staticExperimentalMethodInExperimentalClass' is unstable">staticExperimentalMethodInExperimentalClass</warning>();
    <warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning> experimentalClassInstanceViaExperimentalConstructor = new <warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning>("");
    s = experimentalClassInstanceViaExperimentalConstructor.<warning descr="'experimentalFieldInExperimentalClass' is unstable">experimentalFieldInExperimentalClass</warning>;
    experimentalClassInstanceViaExperimentalConstructor.<warning descr="'experimentalMethodInExperimentalClass' is unstable">experimentalMethodInExperimentalClass</warning>();
    s = <warning descr="'EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS' is unstable">EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS</warning>;
    <warning descr="'staticExperimentalMethodInExperimentalClass' is unstable">staticExperimentalMethodInExperimentalClass</warning>();

    // ---------------------------------

    s = NonExperimentalClass.NON_EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS;
    NonExperimentalClass.staticNonExperimentalMethodInNonExperimentalClass();
    NonExperimentalClass nonExperimentalClassInstanceViaNonExperimentalConstructor = new NonExperimentalClass();
    s = nonExperimentalClassInstanceViaNonExperimentalConstructor.nonExperimentalFieldInNonExperimentalClass;
    nonExperimentalClassInstanceViaNonExperimentalConstructor.nonExperimentalMethodInNonExperimentalClass();
    s = NON_EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS;
    staticNonExperimentalMethodInNonExperimentalClass();

    s = NonExperimentalClass.<warning descr="'EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS' is unstable">EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS</warning>;
    NonExperimentalClass.<warning descr="'staticExperimentalMethodInNonExperimentalClass' is unstable">staticExperimentalMethodInNonExperimentalClass</warning>();
    NonExperimentalClass nonExperimentalClassInstanceViaExperimentalConstructor = new <warning descr="'NonExperimentalClass' is unstable">NonExperimentalClass</warning>("");
    s = nonExperimentalClassInstanceViaExperimentalConstructor.<warning descr="'experimentalFieldInNonExperimentalClass' is unstable">experimentalFieldInNonExperimentalClass</warning>;
    nonExperimentalClassInstanceViaExperimentalConstructor.<warning descr="'experimentalMethodInNonExperimentalClass' is unstable">experimentalMethodInNonExperimentalClass</warning>();
    s = <warning descr="'EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS' is unstable">EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS</warning>;
    <warning descr="'staticExperimentalMethodInNonExperimentalClass' is unstable">staticExperimentalMethodInNonExperimentalClass</warning>();

    // ---------------------------------

    <warning descr="'ExperimentalEnum' is unstable">ExperimentalEnum</warning> nonExperimentalValueInExperimentalEnum = <warning descr="'ExperimentalEnum' is unstable">ExperimentalEnum</warning>.NON_EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM;
    nonExperimentalValueInExperimentalEnum = NON_EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM;
    <warning descr="'ExperimentalEnum' is unstable">ExperimentalEnum</warning> experimentalValueInExperimentalEnum = <warning descr="'ExperimentalEnum' is unstable">ExperimentalEnum</warning>.<warning descr="'EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM' is unstable">EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM</warning>;
    experimentalValueInExperimentalEnum = <warning descr="'EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM' is unstable">EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM</warning>;

    NonExperimentalEnum nonExperimentalValueInNonExperimentalEnum = NonExperimentalEnum.NON_EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM;
    nonExperimentalValueInNonExperimentalEnum = NON_EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM;
    NonExperimentalEnum experimentalValueInNonExperimentalEnum = NonExperimentalEnum.<warning descr="'EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM' is unstable">EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM</warning>;
    experimentalValueInNonExperimentalEnum = <warning descr="'EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM' is unstable">EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM</warning>;
    
    // ---------------------------------

    @<warning descr="'ExperimentalAnnotation' is unstable">ExperimentalAnnotation</warning> class C1 {}
    @<warning descr="'ExperimentalAnnotation' is unstable">ExperimentalAnnotation</warning>(nonExperimentalAttributeInExperimentalAnnotation = "123") class C2 {}
    @<warning descr="'ExperimentalAnnotation' is unstable">ExperimentalAnnotation</warning>(<warning descr="'experimentalAttributeInExperimentalAnnotation' is unstable">experimentalAttributeInExperimentalAnnotation</warning> = "123") class C3 {}
    @NonExperimentalAnnotation class C4 {}
    @NonExperimentalAnnotation(nonExperimentalAttributeInNonExperimentalAnnotation = "123") class C5 {}
    @NonExperimentalAnnotation(<warning descr="'experimentalAttributeInNonExperimentalAnnotation' is unstable">experimentalAttributeInNonExperimentalAnnotation</warning> = "123") class C6 {}
  }
}