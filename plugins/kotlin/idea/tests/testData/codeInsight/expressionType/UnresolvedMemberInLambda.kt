val x = listOf(1).map { it.<caret>noSuchMember }

// K2_TYPE: it.noSuchMember -> Type is unknown
// K2_TYPE: { it.noSuchMember } -> Type is unknown
// K2_TYPE: listOf(1).map { it.noSuchMember } -> Type is unknown
