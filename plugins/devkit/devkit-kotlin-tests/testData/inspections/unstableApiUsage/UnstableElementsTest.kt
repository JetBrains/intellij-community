import pkg.<warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning> 
import pkg.<warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning>.NON_EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS 
import pkg.<warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning>.staticNonExperimentalMethodInExperimentalClass 
import pkg.<warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning>.<warning descr="'EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS' is unstable">EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS</warning> 
import pkg.<warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning>.<warning descr="'staticExperimentalMethodInExperimentalClass' is unstable">staticExperimentalMethodInExperimentalClass</warning> 

import pkg.NonExperimentalClass 
import pkg.NonExperimentalClass.NON_EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS 
import pkg.NonExperimentalClass.staticNonExperimentalMethodInNonExperimentalClass 
import pkg.NonExperimentalClass.<warning descr="'EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS' is unstable">EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS</warning> 
import pkg.NonExperimentalClass.<warning descr="'staticExperimentalMethodInNonExperimentalClass' is unstable">staticExperimentalMethodInNonExperimentalClass</warning> 

import pkg.<warning descr="'ExperimentalEnum' is unstable">ExperimentalEnum</warning> 
import pkg.NonExperimentalEnum 
import pkg.<warning descr="'ExperimentalEnum' is unstable">ExperimentalEnum</warning>.NON_EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM 
import pkg.<warning descr="'ExperimentalEnum' is unstable">ExperimentalEnum</warning>.<warning descr="'EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM' is unstable">EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM</warning> 
import pkg.NonExperimentalEnum.NON_EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM 
import pkg.NonExperimentalEnum.<warning descr="'EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM' is unstable">EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM</warning> 

import pkg.<warning descr="'ExperimentalAnnotation' is unstable">ExperimentalAnnotation</warning> 
import pkg.NonExperimentalAnnotation

import <warning descr="'unstablePkg' is unstable">unstablePkg</warning>.ClassInUnstablePkg 

@Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "UNUSED_VALUE")
class UnstableElementsTest {
  fun test() {
    var s = <warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning>.NON_EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS 
    <warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning>.staticNonExperimentalMethodInExperimentalClass() 
    val experimentalClassInstanceViaNonExperimentalConstructor : <warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning> = ExperimentalClass() 
    s = experimentalClassInstanceViaNonExperimentalConstructor.nonExperimentalFieldInExperimentalClass 
    experimentalClassInstanceViaNonExperimentalConstructor.nonExperimentalMethodInExperimentalClass() 
    s = NON_EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS 
    staticNonExperimentalMethodInExperimentalClass() 

    s = <warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning>.<warning descr="'EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS' is unstable">EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS</warning> 
    <warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning>.<warning descr="'staticExperimentalMethodInExperimentalClass' is unstable">staticExperimentalMethodInExperimentalClass</warning>() 
    val experimentalClassInstanceViaExperimentalConstructor : <warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning> = <warning descr="'ExperimentalClass' is unstable">ExperimentalClass</warning>("") 
    s = experimentalClassInstanceViaExperimentalConstructor.<warning descr="'experimentalFieldInExperimentalClass' is unstable">experimentalFieldInExperimentalClass</warning> 
    experimentalClassInstanceViaExperimentalConstructor.<warning descr="'experimentalMethodInExperimentalClass' is unstable">experimentalMethodInExperimentalClass</warning>() 
    s = <warning descr="'EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS' is unstable">EXPERIMENTAL_CONSTANT_IN_EXPERIMENTAL_CLASS</warning> 
    <warning descr="'staticExperimentalMethodInExperimentalClass' is unstable">staticExperimentalMethodInExperimentalClass</warning>() 

    // ---------------------------------

    s = NonExperimentalClass.NON_EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS 
    NonExperimentalClass.staticNonExperimentalMethodInNonExperimentalClass() 
    val nonExperimentalClassInstanceViaNonExperimentalConstructor = NonExperimentalClass() 
    s = nonExperimentalClassInstanceViaNonExperimentalConstructor.nonExperimentalFieldInNonExperimentalClass 
    nonExperimentalClassInstanceViaNonExperimentalConstructor.nonExperimentalMethodInNonExperimentalClass() 
    s = NON_EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS 
    staticNonExperimentalMethodInNonExperimentalClass() 

    s = NonExperimentalClass.<warning descr="'EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS' is unstable">EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS</warning> 
    NonExperimentalClass.<warning descr="'staticExperimentalMethodInNonExperimentalClass' is unstable">staticExperimentalMethodInNonExperimentalClass</warning>() 
    val nonExperimentalClassInstanceViaExperimentalConstructor = <warning descr="'NonExperimentalClass' is unstable">NonExperimentalClass</warning>("") 
    s = nonExperimentalClassInstanceViaExperimentalConstructor.<warning descr="'experimentalFieldInNonExperimentalClass' is unstable">experimentalFieldInNonExperimentalClass</warning> 
    nonExperimentalClassInstanceViaExperimentalConstructor.<warning descr="'experimentalMethodInNonExperimentalClass' is unstable">experimentalMethodInNonExperimentalClass</warning>() 
    s = <warning descr="'EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS' is unstable">EXPERIMENTAL_CONSTANT_IN_NON_EXPERIMENTAL_CLASS</warning> 
    <warning descr="'staticExperimentalMethodInNonExperimentalClass' is unstable">staticExperimentalMethodInNonExperimentalClass</warning>() 

    // ---------------------------------

    var nonExperimentalValueInExperimentalEnum : <warning descr="'ExperimentalEnum' is unstable">ExperimentalEnum</warning> = <warning descr="'ExperimentalEnum' is unstable">ExperimentalEnum</warning>.NON_EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM 
    nonExperimentalValueInExperimentalEnum = NON_EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM 
    var experimentalValueInExperimentalEnum : <warning descr="'ExperimentalEnum' is unstable">ExperimentalEnum</warning> = <warning descr="'ExperimentalEnum' is unstable">ExperimentalEnum</warning>.<warning descr="'EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM' is unstable">EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM</warning> 
    experimentalValueInExperimentalEnum = <warning descr="'EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM' is unstable">EXPERIMENTAL_VALUE_IN_EXPERIMENTAL_ENUM</warning> 

    var nonExperimentalValueInNonExperimentalEnum = NonExperimentalEnum.NON_EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM 
    nonExperimentalValueInNonExperimentalEnum = NON_EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM 
    var experimentalValueInNonExperimentalEnum = NonExperimentalEnum.<warning descr="'EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM' is unstable">EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM</warning> 
    experimentalValueInNonExperimentalEnum = <warning descr="'EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM' is unstable">EXPERIMENTAL_VALUE_IN_NON_EXPERIMENTAL_ENUM</warning> 

    // ---------------------------------

    @<warning descr="'ExperimentalAnnotation' is unstable">ExperimentalAnnotation</warning> class C1 
    @<warning descr="'ExperimentalAnnotation' is unstable">ExperimentalAnnotation</warning>(nonExperimentalAttributeInExperimentalAnnotation = "123") class C2 
    @<warning descr="'ExperimentalAnnotation' is unstable">ExperimentalAnnotation</warning>(<warning descr="'experimentalAttributeInExperimentalAnnotation' is unstable">experimentalAttributeInExperimentalAnnotation</warning> = "123") class C3 
    @NonExperimentalAnnotation class C4 
    @NonExperimentalAnnotation(nonExperimentalAttributeInNonExperimentalAnnotation = "123") class C5 
    @NonExperimentalAnnotation(<warning descr="'experimentalAttributeInNonExperimentalAnnotation' is unstable">experimentalAttributeInNonExperimentalAnnotation</warning> = "123") class C6 
  }
}