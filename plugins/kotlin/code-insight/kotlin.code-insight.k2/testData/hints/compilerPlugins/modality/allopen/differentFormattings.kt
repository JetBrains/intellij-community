class Outer {
    @Open
    /*<# open #>*/internal
    inner
    class ModifierNewLine

    @Open

    /*a
    ff


     f*/
    /*<# open #>*/internal /*a
    ff


     f*/inner
    /*a
   ff


    f*/class ModifierNewLineComments
}

@Open
         /*<# open #>*/private                class AlotOfSpaces


@Open
   /*fdsfdsfd  */          /*<# open #>*/private         /*fdsfdsfd  */       class AlotOfSpacesAndComments