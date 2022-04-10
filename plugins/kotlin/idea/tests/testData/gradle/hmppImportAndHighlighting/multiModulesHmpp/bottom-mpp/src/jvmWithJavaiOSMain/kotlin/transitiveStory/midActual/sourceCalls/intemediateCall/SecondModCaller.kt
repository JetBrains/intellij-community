package transitiveStory.midActual.sourceCalls.intemediateCall

import transitiveStory.bottomActual.mppBeginning.BottomActualDeclarations
import transitiveStory.bottomActual.mppBeginning.regularTLfunInTheBottomActualCommmon

// https://youtrack.jetbrains.com/issue/KT-33731
import transitiveStory.bottomActual.intermediateSrc.*

class SecondModCaller {
    // ========= api calls (attempt to) ==========
    // java
     val jApiOne = <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: JavaApiContainer'")!>JavaApiContainer<!>()

    // kotlin
     val kApiOne = <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: KotlinApiContainer'")!>KotlinApiContainer<!>()

    // ========= mpp-bottom-actual calls ==========
    // common source set
    val interCallOne = regularTLfunInTheBottomActualCommmon("Some string from `mpp-mid-actual` module")
    val interCallTwo = BottomActualDeclarations.inTheCompanionOfBottomActualDeclarations
    val interCallThree = BottomActualDeclarations().simpleVal

    // https://youtrack.jetbrains.com/issue/KT-33731
    // intermediate source set
    val interCallFour = InBottomActualIntermediate().p
    val interCallFive = IntermediateMPPClassInBottomActual()

    // ========= jvm18 source set (attempt to) ==========
   // java
    val interCallSix = <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: JApiCallerInJVM18'")!>JApiCallerInJVM18<!>()

    // kotlin
    val interCallSeven = <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: Jvm18KApiInheritor'")!>Jvm18KApiInheritor<!>()
    val interCallEight = <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: Jvm18JApiInheritor'")!>Jvm18JApiInheritor<!>()
    val interCallNine = IntermediateMPPClassInBottomActual()
}


// experiments with intermod inheritance
class BottomActualCommonInheritor : BottomActualDeclarations()
expect class <!LINE_MARKER("descr='Has actuals in [multimod-hmpp.bottom-mpp.iosSimLibMain, bottom-mpp] module'")!>BottomActualMPPInheritor<!> : BottomActualDeclarations
