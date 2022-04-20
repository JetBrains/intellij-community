package deft.storage.codegen.field

import deft.storage.codegen.javaName
import org.jetbrains.deft.impl.fields.Field
import java.util.*

val Field<*, *>.implSuspendableCode: String
    get() = "override suspend fun get${javaName.replaceFirstChar {
      if (it.isLowerCase()) it.titlecase(Locale.getDefault())
      else it.toString()
    }}(): ${type.javaType} = $javaName"
