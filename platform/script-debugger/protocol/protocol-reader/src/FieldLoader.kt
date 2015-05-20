package org.jetbrains.protocolReader

class FieldLoader(val name: String, val jsonName: String, val valueReader: ValueReader, val skipRead: Boolean)
