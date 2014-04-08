package org.jetbrains.protocolReader;

import java.util.ArrayList;
import java.util.List;

abstract class ClassScope {
  private final List<TextOutConsumer> additionalMemberTexts = new ArrayList<>(2);
  private final NamePath contextNamespace;
  final DomainGenerator generator;

  ClassScope(DomainGenerator generator, NamePath classNamespace) {
    contextNamespace = classNamespace;
    this.generator = generator;
  }

  protected String getShortClassName() {
    return contextNamespace.getLastComponent();
  }

  String getFullName() {
    return contextNamespace.getFullText();
  }

  NamePath getClassContextNamespace() {
    return contextNamespace;
  }

  void addMember(TextOutConsumer out) {
    additionalMemberTexts.add(out);
  }

  void writeAdditionalMembers(TextOutput out) {
    for (TextOutConsumer deferredWriter : additionalMemberTexts) {
      deferredWriter.append(out);
    }
  }

  protected abstract TypeData.Direction getTypeDirection();
}
