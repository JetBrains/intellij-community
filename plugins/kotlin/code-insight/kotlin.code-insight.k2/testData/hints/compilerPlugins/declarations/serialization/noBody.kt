import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer


@Serializable
class JustClassNoBody/*<# { #>*/
/*<# block [companion   object   { ]
[     fun   serializer ( ) :   KSerializer < JustClassNoBody > ]
[ }]
} #>*/

@Serializable
class JustClassNoBodyWithComments/*fdsfds*//*<# { #>*/
/*<# block [companion   object   { ]
[     fun   serializer ( ) :   KSerializer < JustClassNoBodyWithComments > ]
[ }]
} #>*/

// a lot of empty spaces in the end of the class declarations here
@Serializable
class JustClassNoBodyWithSpacesInTheEnd/*<# { #>*/
/*<# block [companion   object   { ]
[     fun   serializer ( ) :   KSerializer < JustClassNoBodyWithSpacesInTheEnd > ]
[ }]
} #>*/


@Serializable
class JustClassNoBodyWithCommentSingleLine// aaa/*<# { #>*/
/*<# block [companion   object   { ]
[     fun   serializer ( ) :   KSerializer < JustClassNoBodyWithCommentSingleLine > ]
[ }]
} #>*/
