class Incomplete {
    {
        def List l = [0]  //type is inferred to be List<Integer>
        l*.<ref>abs()
    }
}