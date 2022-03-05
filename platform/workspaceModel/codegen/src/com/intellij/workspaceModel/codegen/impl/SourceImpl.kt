package org.jetbrains.deft.impl

class SourceImpl(val name: String) : Source {
    override fun toString(): String = name
}

public interface Source {
}