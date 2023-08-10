import java.io.Serializable

class SerializableClassButDoesNotContainSerialVersionUidField(val myString: String,
                                                              val myInteger: Int,
                                                              val myBoolean: Boolean) : Serializable
