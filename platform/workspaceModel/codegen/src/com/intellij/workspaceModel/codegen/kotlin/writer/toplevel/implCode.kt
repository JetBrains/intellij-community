package deft.storage.codegen

import deft.storage.codegen.field.*
import org.jetbrains.deft.codegen.field.builderSetValueCode
import org.jetbrains.deft.impl.*

fun ObjType<*, *>.implCode(
    additionalImports: String? = null
) = """
import org.jetbrains.deft.*
import org.jetbrains.deft.bytes.*
import org.jetbrains.deft.collections.*
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field
import kotlinx.io.core.Input
import kotlinx.io.core.Output
import kotlinx.coroutines.runBlocking${if (additionalImports?.isNotBlank() == true)"\n$additionalImports\n" else ""}
    
@Suppress("PropertyName")
${ if (abstract) "abstract" else "open" } class $javaImplFqn: $javaImplSuperType(), $javaFullName {
    override val factory: ObjType<*, *>
        get() = $javaFullName

    ${structure.declaredFields.lines("    ") { implCode }.trimEnd()}

    override fun estimateMaxSize(): Int =
        super.estimateMaxSize() +
        ${structure.fieldsToStore.sum("        ") { implEstimateMaxSizeCode }}
                
    override fun storeTo(output: Output) {
        super.storeTo(output)
        ${structure.fieldsToStore.lines("        ") { implStoreCode }.trimEnd()}
    }
    
    override fun loadFrom(data: Input) {
        super.loadFrom(data)
        ${structure.fieldsToStore.lines("        ") { implLoadCode }.trimEnd()}
    }

    override fun updateRefIds() {
        super.updateRefIds()
        ${structure.fieldsToStore.lines("        ") { implUpdateRefIdsCode }.trimEnd()}
    }
    
    override fun moveIntoGraph(value: ObjStorageImpl.ObjGraph?) {
        super.moveIntoGraph(value)
        ${structure.fieldsToStore.lines("        ") { implMoveIntoGraph }.trimEnd()}
    }
            
    override fun checkInitialized() {
        ${structure.allFields.lines("        ") { checkInitializedCode }.trim()}
    }
           
    override fun hasNewValue(field: Field<*, *>): Boolean = when (field) {
        ${structure.allFields.lines("        ") { "${owner.javaFullName}.$javaMetaName -> ${getHasNewValueCode(this@implCode)}" }}            
        else -> alienFieldError(factory, field)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <V> getValue(field: Field<*, V>): V =
        when (field) {
            ${structure.declaredFields.lines("            ") { "${owner.javaFullName}.$javaMetaName -> $javaName as V" }}
            else -> super<$javaImplSuperType>.getValue(field)
        }

    class Builder(override val result: $javaImplFqn): $javaBuilderName, ObjBuilderImpl<$javaFullName>() {
        constructor(): this($javaImplFqn())                 
       
        override var name: String${if (structure.allFields.find { it.name == "name" }?.type as? TString != null) "" else "?"}
            get() = result.name
            set(value) {
                result._name = value
            }

        override var parent: ${structure.allFields.find { it.name == "parent" }?.type?.javaType ?: "Obj?"}
            get() = result.parent
            set(value) {
                result.setParent(value)
            }
        
        ${structure.allNonSystemFields.lines("        ") { builderImplCode }.trimEnd()}
                                                                                   
        override fun <V> getValue(field: Field<*, V>): V = result.getValue(field)

        override fun <V> setValue(field: Field<in $javaFullName, V>, value: V) {
            when (field) {
                ${structure.allFields.lines("                ") { "${owner.javaFullName}.$javaMetaName -> $builderSetValueCode" }}
                else -> alienFieldError(factory, field)
            }
        }                                                                                   
    }
    
    override fun builder(): ObjBuilder<*> = Builder(this)
}
    """.trimIndent()