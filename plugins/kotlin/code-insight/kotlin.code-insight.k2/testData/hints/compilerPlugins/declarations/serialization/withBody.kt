import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer


@Serializable
class JustClassWithBodyNoExtraLine {
/*<# block [[companion   object   { ]
[     fun   serializer ( ) :   KSerializer < JustClassWithBodyNoExtraLine > ]
[ }] <settings-icon>] #>*/
}

@Serializable
class JustClassWithBodyWithExtraLine {

/*<# block [[companion   object   { ]
[     fun   serializer ( ) :   KSerializer < JustClassWithBodyWithExtraLine > ]
[ }] <settings-icon>] #>*/
}

@Serializable
class JustClassWithBodyWithDeclarations {
    val x: String
/*<# block [[companion   object   { ]
[     fun   serializer ( ) :   KSerializer < JustClassWithBodyWithDeclarations > ]
[ }] <settings-icon>] #>*/
}

@Serializable
class JustClassWithBodyWithDeclarationsEmptyLines {



    val x: String = ""

    val y: Int = 10

/*<# block [[companion   object   { ]
[     fun   serializer ( ) :   KSerializer < JustClassWithBodyWithDeclarationsEmptyLines > ]
[ }] <settings-icon>] #>*/
}


@Serializable
class JustClassWithBodyWithDeclarationsRightBraceOnThePrevLine {
/*<# block [[companion   object   { ]
[     fun   serializer ( ) :   KSerializer < JustClassWithBodyWithDeclarationsRightBraceOnThePrevLine > ]
[ }] <settings-icon>] #>*/
    val x = 1}
