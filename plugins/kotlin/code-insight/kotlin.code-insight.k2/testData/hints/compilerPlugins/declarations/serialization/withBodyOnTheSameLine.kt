import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer

class MyClass

@Serializable
class JustClassWithBodyOnTheSameLine {/*<# ... #>*/}

@Serializable
class JustClassWithBodyOnTheSameLineWithSpaces {      /*<# ... #>*/}


@Serializable
class JustClassWithBodyOnTheSameLineWithComments { /*aaa*/   /*bbb*/     /*cccc*//*<# ... #>*/}

@Serializable
class JustClassWithBodyOnTheSameLineWithDeclarationsThere { val x = 10 /*<# ... #>*/}


@Serializable
enum class EnumClassWithBodyOnTheSameLine { A, B, C /*<# ... #>*/}

@Serializable
enum class EnumClassWithBodyOnTheSameLineTrailingComma { A, B, C, /*<# ... #>*/}

@Serializable
enum class EnumClassWithBodyOnTheSameLineTrailingCommaSemi { A, B, C, ; /*<# ... #>*/}

@Serializable
enum class EnumClassWithBodyOnTheSameLineSemi { A, B, C ; /*<# ... #>*/}
