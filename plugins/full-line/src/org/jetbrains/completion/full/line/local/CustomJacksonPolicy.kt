package org.jetbrains.completion.full.line.local

import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.serialization.DefaultXmlSerializationPolicy
import nl.adaptivity.xmlutil.serialization.OutputKind
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerializationPolicy
import nl.adaptivity.xmlutil.serialization.structure.SafeParentInfo

class CustomJacksonPolicy : DefaultXmlSerializationPolicy(false) {
    override fun effectiveOutputKind(serializerParent: SafeParentInfo, tagParent: SafeParentInfo): OutputKind {
        serializerParent.elementUseAnnotations.filterIsInstance<XmlElement>().firstOrNull()?.let {
            return if (it.value) OutputKind.Element else OutputKind.Attribute
        }

        return super.effectiveOutputKind(serializerParent, tagParent)
            .takeIf { it != OutputKind.Attribute } ?: OutputKind.Element
    }

    override fun effectiveName(
        serializerParent: SafeParentInfo,
        tagParent: SafeParentInfo,
        outputKind: OutputKind,
        useName: XmlSerializationPolicy.DeclaredNameInfo
    ): QName {
        return useName.annotatedName
            ?: serializerParent.elemenTypeDescriptor.typeQname
            ?: serialNameToQName(useName.serialName, tagParent.namespace)
    }

}
