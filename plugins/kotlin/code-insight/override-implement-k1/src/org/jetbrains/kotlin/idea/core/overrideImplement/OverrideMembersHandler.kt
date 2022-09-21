// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.overrideImplement

import com.intellij.openapi.project.Project
import com.intellij.util.SmartList
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.core.util.KotlinIdeaCoreBundle
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

private val METHODS_OF_ANY = listOf("equals", "hashCode", "toString").map { FqName("kotlin.Any.$it") }.toSet()

class OverrideMembersHandler(private val preferConstructorParameters: Boolean = false) : GenerateMembersHandler(false) {
    override fun collectMembersToGenerate(descriptor: ClassDescriptor, project: Project): Collection<OverrideMemberChooserObject> {
        val result = ArrayList<OverrideMemberChooserObject>()
        for (member in descriptor.unsubstitutedMemberScope.getContributedDescriptors()) {
            if (member is CallableMemberDescriptor && (member.kind != CallableMemberDescriptor.Kind.DECLARATION)) {
                val overridden = member.overriddenDescriptors
                if (overridden.any { it.modality == Modality.FINAL || DescriptorVisibilities.isPrivate(it.visibility.normalize()) }) continue

                if (DescriptorUtils.isInterface(descriptor) && overridden.any { descriptor.builtIns.isMemberOfAny(it) }) continue

                if ((descriptor.isValue || descriptor.isInline) &&
                    overridden.any { descriptor.builtIns.isMemberOfAny(it) && it.name.asString() in listOf("equals", "hashCode") }
                ) continue

                class Data(
                    val realSuper: CallableMemberDescriptor,
                    val immediateSupers: MutableList<CallableMemberDescriptor> = SmartList()
                )

                val byOriginalRealSupers = LinkedHashMap<CallableMemberDescriptor, Data>()
                for (immediateSuper in overridden) {
                    for (realSuper in toRealSupers(immediateSuper)) {
                        byOriginalRealSupers.getOrPut(realSuper.original) { Data(realSuper) }.immediateSupers.add(immediateSuper)
                    }
                }

                var realSupers = byOriginalRealSupers.values.map(Data::realSuper)
                if (realSupers.size > 1) {
                    realSupers = realSupers.filter { it.fqNameSafe !in METHODS_OF_ANY }
                }

                val nonAbstractRealSupers = realSupers.filter { it.modality != Modality.ABSTRACT }
                val realSupersToUse = if (nonAbstractRealSupers.isNotEmpty()) {
                    nonAbstractRealSupers
                } else {
                    listOf(realSupers.firstOrNull() ?: continue)
                }

                for (realSuper in realSupersToUse) {
                    val immediateSupers = byOriginalRealSupers[realSuper.original]!!.immediateSupers
                    assert(immediateSupers.isNotEmpty())

                    val immediateSuperToUse = if (immediateSupers.size == 1) {
                        immediateSupers.single()
                    } else {
                        immediateSupers.singleOrNull { (it.containingDeclaration as? ClassDescriptor)?.kind == ClassKind.CLASS }
                            ?: immediateSupers.first()
                    }

                    val bodyType = when {
                        descriptor.kind == ClassKind.INTERFACE && realSuper.builtIns.isMemberOfAny(realSuper) ->
                            BodyType.NoBody
                        immediateSuperToUse.modality == Modality.ABSTRACT ->
                            BodyType.FromTemplate
                        realSupersToUse.size == 1 ->
                            BodyType.Super
                        else ->
                            BodyType.QualifiedSuper
                    }

                    result.add(
                        OverrideMemberChooserObject.create(
                            project,
                            realSuper,
                            immediateSuperToUse,
                            bodyType,
                            preferConstructorParameters
                        )
                    )
                }
            }
        }
        return result
    }

    private fun toRealSupers(immediateSuper: CallableMemberDescriptor): Collection<CallableMemberDescriptor> {
        if (immediateSuper.kind != CallableMemberDescriptor.Kind.FAKE_OVERRIDE) {
            return listOf(immediateSuper)
        }
        val overridden = immediateSuper.overriddenDescriptors
        assert(overridden.isNotEmpty())
        return overridden.flatMap { toRealSupers(it) }.distinctBy { it.original }
    }

    override fun getChooserTitle() = KotlinIdeaCoreBundle.message("override.members.handler.title")

    override fun getNoMembersFoundHint() = KotlinIdeaCoreBundle.message("override.members.handler.no.members.hint")
}