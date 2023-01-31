package transitiveStory.bottomActual.intermediateSrc

import transitiveStory.bottomActual.mppBeginning.MPOuter
import transitiveStory.bottomActual.mppBeginning.Outer
import transitiveStory.bottomActual.mppBeginning.tlInternalInCommon

//import transitiveStory.bottomActual.mppBeginning.tlInternalInCommon

class InBottomActualIntermediate {
    val p = 42
    // https://youtrack.jetbrains.com/issue/KT-37264
    val callingInteral = tlInternalInCommon
}

expect class <!LINE_MARKER("descr='Has actuals in [multimod-hmpp.top-mpp.dummyiOSMain, multimod-hmpp.top-mpp.jsMain, multimod-hmpp.top-mpp.jvm18Main] modules'")!>IntermediateMPPClassInBottomActual<!>()


class Subclass : Outer() {
    // a is not visible
    // b, c and d are visible
    // Nested and e are visible

    override val <!LINE_MARKER("descr='Overrides property in 'Outer''")!>b<!> = 5   // 'b' is protected
}

class ChildOfCommonInShared : Outer() {
    override val <!LINE_MARKER("descr='Overrides property in 'Outer''")!>b<!>: Int
        get() = super.b + 243
//    val callAlso = super.c // internal in Outer

    private val other = Nested()
}

class ChildOfMPOuterInShared : MPOuter() {
    private val sav = MPNested()
}
