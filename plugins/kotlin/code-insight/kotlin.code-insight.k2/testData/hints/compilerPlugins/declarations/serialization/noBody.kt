import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer


@Serializable
class JustClassNoBody/*<# { #>*/
/*<# block [[companion   object   { ]
[     fun   serializer ( ) :   KSerializer < JustClassNoBody > ]
[ }] <settings-icon>]
} #>*/

@Serializable
class JustClassNoBodyWithComments/*fdsfds*//*<# { #>*/
/*<# block [[companion   object   { ]
[     fun   serializer ( ) :   KSerializer < JustClassNoBodyWithComments > ]
[ }] <settings-icon>]
} #>*/

// a lot of empty spaces in the end of the class declarations here
@Serializable
class JustClassNoBodyWithSpacesInTheEnd/*<# { #>*/
/*<# block [[companion   object   { ]
[     fun   serializer ( ) :   KSerializer < JustClassNoBodyWithSpacesInTheEnd > ]
[ }] <settings-icon>]
} #>*/


@Serializable
class JustClassNoBodyWithCommentSingleLine// aaa/*<# { #>*/
/*<# block [[companion   object   { ]
[     fun   serializer ( ) :   KSerializer < JustClassNoBodyWithCommentSingleLine > ]
[ }] <settings-icon>]
} #>*/
