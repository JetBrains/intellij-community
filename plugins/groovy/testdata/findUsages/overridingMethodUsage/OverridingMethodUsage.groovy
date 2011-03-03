class Bar {
    boolean isFo<caret>cused() { }
}

class BarImpl extends Bar {
    @Override
    boolean isFocused() { }
}

println new BarImpl().focused
println new BarImpl().isFocused()