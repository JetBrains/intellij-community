class A {
    <weak_warning descr="Function 'sdfsdfsd' could be private">public</weak_warning> fun sdfsdfsd() {}
    <weak_warning descr="Function 'sdflkj' could be private">internal</weak_warning> fun sdflkj() {}
    public external fun sssss()
    override fun hashCode(): Int {
        sdfsdfsd()
        sdflkj()
        sssss()
        return super.hashCode()
    }
}