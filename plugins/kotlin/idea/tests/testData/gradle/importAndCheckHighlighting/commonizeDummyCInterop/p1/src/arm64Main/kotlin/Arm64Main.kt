import dummy.DummyStruct
import dummy.OtherStruct

actual fun <lineMarker descr="Has declaration in common module">sum</lineMarker>(dummy: DummyStruct): Double {
    return dummy.myShort + dummy.myInteger + dummy.myLong + dummy.myFloat + dummy.myDouble
}

actual fun <lineMarker descr="Has declaration in common module">sum</lineMarker>(otherStruct: OtherStruct): Double {
    return sum(otherStruct.dummy1) + sum(otherStruct.dummy2)
}

actual fun DummyStruct.<lineMarker descr="Has declaration in common module">reset</lineMarker>() {
    this.myShort = 0
    this.myInteger = 0
    this.myLong = 0
    this.myFloat = 0f
    this.myDouble = 0.0
}
