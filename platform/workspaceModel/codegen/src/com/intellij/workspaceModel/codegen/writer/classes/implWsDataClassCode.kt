package org.jetbrains.deft.codegen.ijws.classes

import deft.storage.codegen.implFieldName
import deft.storage.codegen.javaFullName
import deft.storage.codegen.javaImplBuilderName
import deft.storage.codegen.javaImplName
import org.jetbrains.deft.Obj
import org.jetbrains.deft.codegen.ijws.fields.implWsDataFieldCode
import org.jetbrains.deft.codegen.ijws.fields.implWsDataFieldInitializedCode
import org.jetbrains.deft.codegen.ijws.isRefType
import org.jetbrains.deft.codegen.ijws.model.WsEntityWithPersistentId
import org.jetbrains.deft.codegen.ijws.sups
import org.jetbrains.deft.codegen.ijws.wsFqn
import org.jetbrains.deft.codegen.model.DefType
import org.jetbrains.deft.codegen.utils.lines
import org.jetbrains.deft.impl.*
import org.jetbrains.deft.impl.fields.Field

/**
 * - Soft links
 * - with PersistentId
 */

val ObjType<*, *>.javaDataName
    get() = "${name.replace(".", "")}Data"

val ObjType<*, *>.isEntityWithPersistentId: Boolean
    get() = (this as? DefType)?.def?.kind is WsEntityWithPersistentId

fun ObjType<*, *>.implWsDataClassCode(simpleTypes: List<DefType>): String {
    val entityDataBaseClass = if (isEntityWithPersistentId) {
        "${wsFqn("WorkspaceEntityData")}.WithCalculablePersistentId<$javaFullName>()"
    } else {
        "${wsFqn("WorkspaceEntityData")}<$javaFullName>()"
    }
    val hasSoftLinks = hasSoftLinks(simpleTypes)
    val softLinkable = if (hasSoftLinks) wsFqn("SoftLinkable") else null
    return lines {
        section("class $javaDataName : ${sups(entityDataBaseClass, softLinkable)}") label@ {
            listNl(structure.allFields.noRefs().noEntitySource().noPersistentId()) { implWsDataFieldCode }

            listNl(structure.allFields.noRefs().noEntitySource().noPersistentId().noOptional()) { implWsDataFieldInitializedCode }

            softLinksCode(this@label, hasSoftLinks, simpleTypes)

            sectionNl("override fun wrapAsModifiable(diff: ${wsFqn("WorkspaceEntityStorageBuilder")}): ${wsFqn("ModifiableWorkspaceEntity")}<$javaFullName>") {
                line("val modifiable = $javaImplBuilderName(null)")
                line("modifiable.allowModifications {")
                line("  modifiable.diff = diff")
                line("  modifiable.snapshot = diff")
                line("  modifiable.id = createEntityId()")
                line("  modifiable.entitySource = this.entitySource")
                line("}")
                line("return modifiable")
            }

            // --- createEntity
            sectionNl("override fun createEntity(snapshot: ${wsFqn("WorkspaceEntityStorage")}): $javaFullName") {
                line("val entity = $javaImplName()")
                list(structure.allFields.noRefs().noEntitySource().noPersistentId()) {
                    "entity.$implFieldName = $name"
                }
                line("entity.entitySource = entitySource")
                line("entity.snapshot = snapshot")
                line("entity.id = createEntityId()")
                line("return entity")
            }

            conditionalLine({ isEntityWithPersistentId }, "override fun persistentId(): ${wsFqn("PersistentEntityId")}<*>") {
                val persistentIdField = structure.allFields.first { it.name == "persistentId" }
                assert(persistentIdField.hasDefault  == Field.Default.plain)
                val methodBody = persistentIdField.defaultValue!!
                if (methodBody.startsWith("=")) {
                    line("return ${methodBody.substring(2)}")
                } else {
                    line(methodBody)
                }
            }

            // --- equals
            sectionNl("override fun equals(other: Any?): Boolean") {
                line("if (other == null) return false")
                line("if (this::class != other::class) return false")

                lineWrapped("other as $javaDataName")

                list(structure.allFields.noRefs().noPersistentId()) {
                    "if (this.$name != other.$name) return false"
                }

                line("return true")
            }

            // --- equalsIgnoringEntitySource
            sectionNl("override fun equalsIgnoringEntitySource(other: Any?): Boolean") {
                line("if (other == null) return false")
                line("if (this::class != other::class) return false")

                lineWrapped("other as $javaDataName")

                list(structure.allFields.noRefs().noEntitySource().noPersistentId()) {
                    "if (this.$name != other.$name) return false"
                }

                line("return true")
            }

            // --- hashCode
            section("override fun hashCode(): Int") {
                line("var result = entitySource.hashCode()")
                list(structure.allFields.noRefs().noEntitySource().noPersistentId()) {
                    "result = 31 * result + $name.hashCode()"
                }
                line("return result")
            }
        }
    }
}

fun List<Field<out Obj, Any?>>.noRefs(): List<Field<out Obj, Any?>> = this.filterNot { it.type.isRefType() }
fun List<Field<out Obj, Any?>>.noEntitySource() = this.filter { it.name != "entitySource" }
fun List<Field<out Obj, Any?>>.noPersistentId() = this.filter { it.name != "persistentId" }
fun List<Field<out Obj, Any?>>.noOptional() = this.filter { it.type !is TOptional<*> }
fun List<Field<out Obj, Any?>>.noId() = this.filter { it.name != "id" }
fun List<Field<out Obj, Any?>>.noSnapshot() = this.filter { it.name != "snapshot" }
