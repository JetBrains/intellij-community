interface Parameter {
    val interface_p1: String
    val interface_p2: String
}

class ParameterImpl(
    override val interface_p1: String,
    private val ctor_private: String,
    val ctor_val: String,
    var ctor_var: String,
    ctor_param: String,
): Parameter {
    override val interface_p2: String = ""
    fun foo(fun_param: String) {
        val local_val = 1
    }
}
