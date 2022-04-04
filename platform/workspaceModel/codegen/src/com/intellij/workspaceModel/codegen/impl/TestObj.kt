package org.jetbrains.deft.impl

import kotlinx.io.core.Input
import kotlinx.io.core.Output
import org.jetbrains.deft.*
import org.jetbrains.deft.impl.fields.Field
import org.jetbrains.deft.runtime.Runtime

object TestObjModule : ObjModule(Id("org.jetbrains.deft.impl.test")) {

    @InitApi
    override fun init() {
        requireDependency(Runtime)
        beginInit(1)
        add(TestObj)
    }
}

class TestObj : ObjImpl() {
    override val factory: ObjType<*, *>
        get() = TestObj

    override fun hasNewValue(field: Field<*, *>): Boolean = true
    override fun checkInitialized() = Unit

    @set:JvmName("testObjSetParent")
    override var parent: Obj
        get() = super.parent as RootImpl
        set(value) = setParent(value)

    var _aId: ObjId<*> = ObjId.nothing
    var _a: ObjImpl? = null
    var a: ObjImpl?
        get() {
            _a = _getRef(_a, _aId)
            return _a
        }
        set(value) {
            _a = _setRef(value)
            _aId = value?._id ?: ObjId.nothing
        }

    class Builder : ObjBuilderImpl<TestObj>() {
        override val result = TestObj()

        var a: ObjImpl?
            get() = result.a
            set(value) {
                result.a = value
            }
    }

    companion object : ObjType<TestObj, Builder>(TestObjModule, 1) {
        override fun loadBuilderFactory(): () -> Builder = ::Builder
    }
}

fun obj(name: String) = TestObj().also { it._name = name }