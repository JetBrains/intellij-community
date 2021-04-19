package com.intellij.openapi.vfs

abstract class VirtualFile {

  fun test() {
    if (this === this) {}
    if (this !== this) {}

    val first: VirtualFileImpl = VirtualFileImpl()
    val second: VirtualFile? = first

    if (this === second) {}
    if (second !== this) {}

    if (<warning descr="'VirtualFile' instances should be compared by 'equals()', not '=='">first === second</warning>) {}
    if (<warning descr="'VirtualFile' instances should be compared by 'equals()', not '=='">first !== second</warning>) {}

    if (<warning descr="'VirtualFile' instances should be compared by 'equals()', not '=='">second === first</warning>) {}
    if (<warning descr="'VirtualFile' instances should be compared by 'equals()', not '=='">second !== first</warning>) {}

    val third: MyVirtualFile = first

    if (<warning descr="'VirtualFile' instances should be compared by 'equals()', not '=='">third === first</warning>) {}
    if (<warning descr="'VirtualFile' instances should be compared by 'equals()', not '=='">third !== first</warning>) {}

    val fourth: NullableVirtualFile = second
    if (<warning descr="'VirtualFile' instances should be compared by 'equals()', not '=='">fourth === second</warning>) {}
    if (<warning descr="'VirtualFile' instances should be compared by 'equals()', not '=='">fourth !== second</warning>) {}
  }
}

class VirtualFileImpl : VirtualFile()

typealias MyVirtualFile = VirtualFileImpl

typealias NullableVirtualFile = VirtualFile?