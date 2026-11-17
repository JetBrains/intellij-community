// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
class Foo {
    String name
    int count
    String[] names
    int[] numbers

    @Override
    public String toString() {
        return """\
Foo{
    name='$name',
    count=$count,
    names=${Arrays.toString(names)},
    numbers=${Arrays.toString(numbers)}
}"""
    }
}