package deft.storage.codegen

import org.jetbrains.deft.Obj
import org.jetbrains.deft.codegen.utils.fqn
import org.jetbrains.deft.impl.ObjType
import org.jetbrains.deft.impl.TStructure
import org.jetbrains.deft.impl.fields.Field

val ObjType<*, *>.javaFullName
    get() = fqn(packageName, name)

val ObjType<*, *>.javaSimpleName
    get() = name.substringAfterLast('.')

val ObjType<*, *>.javaBuilderName
    get() = "$name.Builder"

val ObjType<*, *>.javaImplName
    get() = "${name.replace(".", "")}Impl"

val ObjType<*, *>.javaImplFqn
    get() = fqn(packageName, javaImplName)

val ObjType<*, *>.javaImplBuilderName
    get() = "${javaImplName}.Builder"

val ObjType<*, *>.javaSuperType
    get() = if (base == null) "Obj" else base!!.javaSimpleName

val ObjType<*, *>.javaImplSuperType
    get() = if (base == null) "ObjImpl" else base!!.javaImplFqn

val TStructure<*, *>.fieldsToStore: List<Field<out Obj, Any?>>
    get() = newFields.filter { !it.isOverride && it.hasDefault == Field.Default.none }

val TStructure<*, *>.builderFields: List<Field<out Obj, Any?>>
    get() = allFields.filter { it.hasDefault == Field.Default.none }

val TStructure<*, *>.allNonSystemFields: List<Field<out Obj, Any?>>
    get() = allFields.filter { it.name != "parent" && it.name != "name" }
