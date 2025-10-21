import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer


@Serializable
class JustClassWithBodyNoExtraLine {
/*<# block [companion   object   { ]
[     fun   serializer ( ) :   KSerializer < JustClassWithBodyNoExtraLine > ]
[ }] #>*/
}

@Serializable
class JustClassWithBodyWithExtraLine {

/*<# block [companion   object   { ]
[     fun   serializer ( ) :   KSerializer < JustClassWithBodyWithExtraLine > ]
[ }] #>*/
}

@Serializable
class JustClassWithBodyWithDeclarations {
    val x: String
/*<# block [companion   object   { ]
[     fun   serializer ( ) :   KSerializer < JustClassWithBodyWithDeclarations > ]
[ }] #>*/
}

@Serializable
class JustClassWithBodyWithDeclarationsEmptyLines {



    val x: String = ""

    val y: Int = 10

/*<# block [companion   object   { ]
[     fun   serializer ( ) :   KSerializer < JustClassWithBodyWithDeclarationsEmptyLines > ]
[ }] #>*/
}


@Serializable
class JustClassWithBodyWithDeclarationsRightBraceOnThePrevLine {
/*<# block [companion   object   { ]
[     fun   serializer ( ) :   KSerializer < JustClassWithBodyWithDeclarationsRightBraceOnThePrevLine > ]
[ }] #>*/
    val x = 1}
