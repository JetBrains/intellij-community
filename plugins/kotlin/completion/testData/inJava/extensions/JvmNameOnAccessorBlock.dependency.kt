package a

class Target

var Target.value: String
    get() = ""
    @JvmName("customSetter")
    set(v) {}
