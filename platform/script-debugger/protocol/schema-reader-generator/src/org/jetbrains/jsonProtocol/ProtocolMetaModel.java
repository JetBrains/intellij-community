package org.jetbrains.jsonProtocol;

import org.chromium.protocolReader.JsonField;
import org.chromium.protocolReader.JsonOptionalField;
import org.chromium.protocolReader.JsonType;

import java.util.List;

/**
 * Defines schema of WIP metamodel defined in http://svn.webkit.org/repository/webkit/trunk/Source/WebCore/inspector/Inspector.json
 */
public interface ProtocolMetaModel {
  @JsonType
  interface Root {
    @JsonOptionalField
    Version version();

    List<Domain> domains();
  }

  @JsonType
  interface Version {
    String major();
    String minor();
  }

  @JsonType
  interface Domain {
    String domain();

    @JsonOptionalField
    List<StandaloneType> types();

    List<Command> commands();

    @JsonOptionalField
    List<Event> events();

    @JsonOptionalField
    String description();

    @JsonOptionalField
    boolean hidden();
  }

  @JsonType
  interface Command {
    String name();

    @JsonOptionalField
    List<Parameter> parameters();

    @JsonOptionalField
    List<Parameter> returns();

    @JsonOptionalField
    String description();

    @JsonOptionalField
    boolean hidden();

    @JsonOptionalField
    boolean async();
  }

  @JsonType
  interface Parameter extends ItemDescriptor.Named {
    @Override
    String name();

    @Override
    @JsonOptionalField
    String shortName();

    @Override
    @JsonOptionalField
    String type();

    @Override
    @JsonOptionalField
    ArrayItemType items();

    @Override
    @JsonField(jsonLiteralName = "enum")
    @JsonOptionalField
    List<String> getEnum();

    @Override
    @JsonOptionalField
    @JsonField(jsonLiteralName = "$ref")
    String ref();

    @Override
    @JsonOptionalField
    boolean optional();

    @Override
    @JsonOptionalField
    String description();

    @JsonOptionalField
    boolean hidden();
  }

  @JsonType
  interface Event {
    String name();

    @JsonOptionalField
    List<Parameter> parameters();

    @JsonOptionalField
    String description();

    @JsonOptionalField
    boolean hidden();
  }

  @JsonType
  interface StandaloneType extends ItemDescriptor.Type {
    String id();

    @Override
    @JsonOptionalField
    String description();

    @Override
    String type();

    @JsonOptionalField
    boolean hidden();

    @Override
    @JsonOptionalField
    List<ObjectProperty> properties();

    @Override
    @JsonField(jsonLiteralName = "enum")
    @JsonOptionalField
    List<String> getEnum();

    @Override
    @JsonOptionalField
    ArrayItemType items();
  }


  @JsonType
  interface ArrayItemType extends ItemDescriptor.Type, ItemDescriptor.Referenceable {
    @Override
    @JsonOptionalField
    String description();

    @JsonOptionalField
    boolean optional();

    @Override
    @JsonOptionalField
    String type();

    @Override
    @JsonOptionalField
    ArrayItemType items();

    @Override
    @JsonField(jsonLiteralName = "$ref")
    @JsonOptionalField
    String ref();

    @Override
    @JsonField(jsonLiteralName = "enum")
    @JsonOptionalField
    List<String> getEnum();

    @Override
    @JsonOptionalField
    List<ObjectProperty> properties();
  }

  @JsonType
  interface ObjectProperty extends ItemDescriptor.Named {
    @Override
    String name();

    @Override
    @JsonOptionalField
    String shortName();

    @Override
    @JsonOptionalField
    String description();

    @Override
    @JsonOptionalField
    boolean optional();

    @Override
    @JsonOptionalField
    String type();

    @Override
    @JsonOptionalField
    ArrayItemType items();

    @Override
    @JsonField(jsonLiteralName = "$ref")
    @JsonOptionalField
    String ref();

    @Override
    @JsonField(jsonLiteralName = "enum")
    @JsonOptionalField
    List<String> getEnum();

    @JsonOptionalField
    boolean hidden();
  }

  String STRING_TYPE = "string";
  String INTEGER_TYPE = "integer";
  String NUMBER_TYPE = "number";
  String BOOLEAN_TYPE = "boolean";
  String OBJECT_TYPE = "object";
  String ARRAY_TYPE = "array";
  String UNKNOWN_TYPE = "unknown";
  String ANY_TYPE = "any";
}
