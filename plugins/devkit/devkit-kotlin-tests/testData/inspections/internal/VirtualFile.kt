package com.intellij.openapi.vfs

abstract class VirtualFile {

  fun test() {
    if (this === this) {}
    if (this !== this) {}

    val first: VirtualFileImpl = VirtualFileImpl()
    val second: VirtualFile? = first

    if (this === second) {}
    if (second !== this) {}

    if (<warning descr="'VirtualFile' instances should be compared for equality, not identity">first === second</warning>) {}
    if (<warning descr="'VirtualFile' instances should be compared for equality, not identity">first !== second</warning>) {}

    if (<warning descr="'VirtualFile' instances should be compared for equality, not identity">second === first</warning>) {}
    if (<warning descr="'VirtualFile' instances should be compared for equality, not identity">second !== first</warning>) {}

    val third: MyVirtualFile = first

    if (<warning descr="'VirtualFile' instances should be compared for equality, not identity">third === first</warning>) {}
    if (<warning descr="'VirtualFile' instances should be compared for equality, not identity">third !== first</warning>) {}

    val fourth: NullableVirtualFile = second
    if (<warning descr="'VirtualFile' instances should be compared for equality, not identity">fourth === second</warning>) {}
    if (<warning descr="'VirtualFile' instances should be compared for equality, not identity">fourth !== second</warning>) {}
  }
}

class VirtualFileImpl : VirtualFile()

typealias MyVirtualFile = VirtualFileImpl

typealias NullableVirtualFile = VirtualFile?