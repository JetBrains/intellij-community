// Generated source
package org.jetbrains.jsonProtocol;

import org.jetbrains.jsonProtocol.*;

import static org.jetbrains.jsonProtocol.JsonReaders.*;

public final class ProtocolSchemaReaderImpl implements org.jetbrains.jsonProtocol.ProtocolSchemaReader {
  @Override
  public org.jetbrains.jsonProtocol.ProtocolMetaModel.Root parseRoot(org.jetbrains.io.JsonReaderEx reader) {
    return new M6(reader);
  }

  public static final class M4 implements org.jetbrains.jsonProtocol.ProtocolMetaModel.ObjectProperty {
    private String _description;
    private java.util.List<String> _enum;
    private boolean _hidden;
    private org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType _items;
    private String _name;
    private boolean _optional;
    private String _$ref;
    private String _shortName;
    private String _type;

    public M4(org.jetbrains.io.JsonReaderEx reader) {
      reader.beginObject();
      while (reader.hasNext()) {
        CharSequence name = reader.nextNameAsCharSequence();
        if (name.equals("description")) {
          _description = readNullableString(reader);
        }
        else if (name.equals("enum")) {
          _enum = nextList(reader);
        }
        else if (name.equals("hidden")) {
          _hidden = readBoolean(reader, "hidden");
        }
        else if (name.equals("items")) {
          _items = new M0(reader);
        }
        else if (name.equals("name")) {
          _name = readString(reader, "name");
        }
        else if (name.equals("optional")) {
          _optional = readBoolean(reader, "optional");
        }
        else if (name.equals("$ref")) {
          _$ref = readNullableString(reader);
        }
        else if (name.equals("shortName")) {
          _shortName = readNullableString(reader);
        }
        else if (name.equals("type")) {
          _type = readNullableString(reader);
        }
        else {
          reader.skipValue();
        }
      }
      reader.endObject();
    }

    @Override
    public java.lang.String description() {
      return _description;
    }

    @Override
    public java.util.List<java.lang.String> getEnum() {
      return _enum;
    }

    @Override
    public boolean hidden() {
      return _hidden;
    }

    @Override
    public org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType items() {
      return _items;
    }

    @Override
    public java.lang.String name() {
      return _name;
    }

    @Override
    public boolean optional() {
      return _optional;
    }

    @Override
    public java.lang.String ref() {
      return _$ref;
    }

    @Override
    public java.lang.String shortName() {
      return _shortName;
    }

    @Override
    public java.lang.String type() {
      return _type;
    }
  }

  public static final class M0 implements org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType {
    private String _description;
    private java.util.List<String> _enum;
    private org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType _items;
    private boolean _optional;
    private java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.ObjectProperty> _properties;
    private String _$ref;
    private String _type;

    public M0(org.jetbrains.io.JsonReaderEx reader) {
      reader.beginObject();
      while (reader.hasNext()) {
        CharSequence name = reader.nextNameAsCharSequence();
        if (name.equals("description")) {
          _description = readNullableString(reader);
        }
        else if (name.equals("enum")) {
          _enum = nextList(reader);
        }
        else if (name.equals("items")) {
          _items = new M0(reader);
        }
        else if (name.equals("optional")) {
          _optional = readBoolean(reader, "optional");
        }
        else if (name.equals("properties")) {
          _properties = readObjectArray(reader, "properties", new M4F(), true);
        }
        else if (name.equals("$ref")) {
          _$ref = readNullableString(reader);
        }
        else if (name.equals("type")) {
          _type = readNullableString(reader);
        }
        else {
          reader.skipValue();
        }
      }
      reader.endObject();
    }

    @Override
    public java.lang.String description() {
      return _description;
    }

    @Override
    public java.util.List<java.lang.String> getEnum() {
      return _enum;
    }

    @Override
    public org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType items() {
      return _items;
    }

    @Override
    public boolean optional() {
      return _optional;
    }

    @Override
    public java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.ObjectProperty> properties() {
      return _properties;
    }

    @Override
    public java.lang.String ref() {
      return _$ref;
    }

    @Override
    public java.lang.String type() {
      return _type;
    }
  }

  public static final class M7 implements org.jetbrains.jsonProtocol.ProtocolMetaModel.StandaloneType {
    private String _description;
    private java.util.List<String> _enum;
    private boolean _hidden;
    private String _id;
    private org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType _items;
    private java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.ObjectProperty> _properties;
    private String _type;

    public M7(org.jetbrains.io.JsonReaderEx reader) {
      reader.beginObject();
      while (reader.hasNext()) {
        CharSequence name = reader.nextNameAsCharSequence();
        if (name.equals("description")) {
          _description = readNullableString(reader);
        }
        else if (name.equals("enum")) {
          _enum = nextList(reader);
        }
        else if (name.equals("hidden")) {
          _hidden = readBoolean(reader, "hidden");
        }
        else if (name.equals("id")) {
          _id = readString(reader, "id");
        }
        else if (name.equals("items")) {
          _items = new M0(reader);
        }
        else if (name.equals("properties")) {
          _properties = readObjectArray(reader, "properties", new M4F(), true);
        }
        else if (name.equals("type")) {
          _type = readString(reader, "type");
        }
        else {
          reader.skipValue();
        }
      }
      reader.endObject();
    }

    @Override
    public java.lang.String description() {
      return _description;
    }

    @Override
    public java.util.List<java.lang.String> getEnum() {
      return _enum;
    }

    @Override
    public boolean hidden() {
      return _hidden;
    }

    @Override
    public java.lang.String id() {
      return _id;
    }

    @Override
    public org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType items() {
      return _items;
    }

    @Override
    public java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.ObjectProperty> properties() {
      return _properties;
    }

    @Override
    public java.lang.String type() {
      return _type;
    }
  }

  public static final class M3 implements org.jetbrains.jsonProtocol.ProtocolMetaModel.Event {
    private String _description;
    private boolean _hidden;
    private String _name;
    private java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter> _parameters;

    public M3(org.jetbrains.io.JsonReaderEx reader) {
      reader.beginObject();
      while (reader.hasNext()) {
        CharSequence name = reader.nextNameAsCharSequence();
        if (name.equals("description")) {
          _description = readNullableString(reader);
        }
        else if (name.equals("hidden")) {
          _hidden = readBoolean(reader, "hidden");
        }
        else if (name.equals("name")) {
          _name = readString(reader, "name");
        }
        else if (name.equals("parameters")) {
          _parameters = readObjectArray(reader, "parameters", new M5F(), true);
        }
        else {
          reader.skipValue();
        }
      }
      reader.endObject();
    }

    @Override
    public java.lang.String description() {
      return _description;
    }

    @Override
    public boolean hidden() {
      return _hidden;
    }

    @Override
    public java.lang.String name() {
      return _name;
    }

    @Override
    public java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter> parameters() {
      return _parameters;
    }
  }

  public static final class M5 implements org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter {
    private String _description;
    private java.util.List<String> _enum;
    private boolean _hidden;
    private org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType _items;
    private String _name;
    private boolean _optional;
    private String _$ref;
    private String _shortName;
    private String _type;

    public M5(org.jetbrains.io.JsonReaderEx reader) {
      reader.beginObject();
      while (reader.hasNext()) {
        CharSequence name = reader.nextNameAsCharSequence();
        if (name.equals("description")) {
          _description = readNullableString(reader);
        }
        else if (name.equals("enum")) {
          _enum = nextList(reader);
        }
        else if (name.equals("hidden")) {
          _hidden = readBoolean(reader, "hidden");
        }
        else if (name.equals("items")) {
          _items = new M0(reader);
        }
        else if (name.equals("name")) {
          _name = readString(reader, "name");
        }
        else if (name.equals("optional")) {
          _optional = readBoolean(reader, "optional");
        }
        else if (name.equals("$ref")) {
          _$ref = readNullableString(reader);
        }
        else if (name.equals("shortName")) {
          _shortName = readNullableString(reader);
        }
        else if (name.equals("type")) {
          _type = readNullableString(reader);
        }
        else {
          reader.skipValue();
        }
      }
      reader.endObject();
    }

    @Override
    public java.lang.String description() {
      return _description;
    }

    @Override
    public java.util.List<java.lang.String> getEnum() {
      return _enum;
    }

    @Override
    public boolean hidden() {
      return _hidden;
    }

    @Override
    public org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType items() {
      return _items;
    }

    @Override
    public java.lang.String name() {
      return _name;
    }

    @Override
    public boolean optional() {
      return _optional;
    }

    @Override
    public java.lang.String ref() {
      return _$ref;
    }

    @Override
    public java.lang.String shortName() {
      return _shortName;
    }

    @Override
    public java.lang.String type() {
      return _type;
    }
  }

  public static final class M1 implements org.jetbrains.jsonProtocol.ProtocolMetaModel.Command {
    private boolean _async;
    private String _description;
    private boolean _hidden;
    private String _name;
    private java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter> _parameters;
    private java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter> _returns;

    public M1(org.jetbrains.io.JsonReaderEx reader) {
      reader.beginObject();
      while (reader.hasNext()) {
        CharSequence name = reader.nextNameAsCharSequence();
        if (name.equals("async")) {
          _async = readBoolean(reader, "async");
        }
        else if (name.equals("description")) {
          _description = readNullableString(reader);
        }
        else if (name.equals("hidden")) {
          _hidden = readBoolean(reader, "hidden");
        }
        else if (name.equals("name")) {
          _name = readString(reader, "name");
        }
        else if (name.equals("parameters")) {
          _parameters = readObjectArray(reader, "parameters", new M5F(), true);
        }
        else if (name.equals("returns")) {
          _returns = readObjectArray(reader, "returns", new M5F(), true);
        }
        else {
          reader.skipValue();
        }
      }
      reader.endObject();
    }

    @Override
    public boolean async() {
      return _async;
    }

    @Override
    public java.lang.String description() {
      return _description;
    }

    @Override
    public boolean hidden() {
      return _hidden;
    }

    @Override
    public java.lang.String name() {
      return _name;
    }

    @Override
    public java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter> parameters() {
      return _parameters;
    }

    @Override
    public java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter> returns() {
      return _returns;
    }
  }

  public static final class M2 implements org.jetbrains.jsonProtocol.ProtocolMetaModel.Domain {
    private java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Command> _commands;
    private String _description;
    private String _domain;
    private java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Event> _events;
    private boolean _hidden;
    private java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.StandaloneType> _types;

    public M2(org.jetbrains.io.JsonReaderEx reader) {
      reader.beginObject();
      while (reader.hasNext()) {
        CharSequence name = reader.nextNameAsCharSequence();
        if (name.equals("commands")) {
          _commands = readObjectArray(reader, "commands", new M1F(), false);
        }
        else if (name.equals("description")) {
          _description = readNullableString(reader);
        }
        else if (name.equals("domain")) {
          _domain = readString(reader, "domain");
        }
        else if (name.equals("events")) {
          _events = readObjectArray(reader, "events", new M3F(), true);
        }
        else if (name.equals("hidden")) {
          _hidden = readBoolean(reader, "hidden");
        }
        else if (name.equals("types")) {
          _types = readObjectArray(reader, "types", new M7F(), true);
        }
        else {
          reader.skipValue();
        }
      }
      reader.endObject();
    }

    @Override
    public java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Command> commands() {
      return _commands;
    }

    @Override
    public java.lang.String description() {
      return _description;
    }

    @Override
    public java.lang.String domain() {
      return _domain;
    }

    @Override
    public java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Event> events() {
      return _events;
    }

    @Override
    public boolean hidden() {
      return _hidden;
    }

    @Override
    public java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.StandaloneType> types() {
      return _types;
    }
  }

  public static final class M8 implements org.jetbrains.jsonProtocol.ProtocolMetaModel.Version {
    private String _major;
    private String _minor;

    public M8(org.jetbrains.io.JsonReaderEx reader) {
      reader.beginObject();
      while (reader.hasNext()) {
        CharSequence name = reader.nextNameAsCharSequence();
        if (name.equals("major")) {
          _major = readString(reader, "major");
        }
        else if (name.equals("minor")) {
          _minor = readString(reader, "minor");
        }
        else {
          reader.skipValue();
        }
      }
      reader.endObject();
    }

    @Override
    public java.lang.String major() {
      return _major;
    }

    @Override
    public java.lang.String minor() {
      return _minor;
    }
  }

  public static final class M6 implements org.jetbrains.jsonProtocol.ProtocolMetaModel.Root {
    private java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Domain> _domains;
    private org.jetbrains.jsonProtocol.ProtocolMetaModel.Version _version;

    public M6(org.jetbrains.io.JsonReaderEx reader) {
      reader.beginObject();
      while (reader.hasNext()) {
        CharSequence name = reader.nextNameAsCharSequence();
        if (name.equals("domains")) {
          _domains = readObjectArray(reader, "domains", new M2F(), false);
        }
        else if (name.equals("version")) {
          _version = new M8(reader);
        }
        else {
          reader.skipValue();
        }
      }
      reader.endObject();
    }

    @Override
    public java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Domain> domains() {
      return _domains;
    }

    @Override
    public org.jetbrains.jsonProtocol.ProtocolMetaModel.Version version() {
      return _version;
    }
  }

  static final class M4F extends ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.ObjectProperty> {
    @Override
    public org.jetbrains.jsonProtocol.ProtocolMetaModel.ObjectProperty read(org.jetbrains.io.JsonReaderEx reader) {
      return new M4(reader);
    }
  }

  static final class M5F extends ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter> {
    @Override
    public org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter read(org.jetbrains.io.JsonReaderEx reader) {
      return new M5(reader);
    }
  }

  static final class M1F extends ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.Command> {
    @Override
    public org.jetbrains.jsonProtocol.ProtocolMetaModel.Command read(org.jetbrains.io.JsonReaderEx reader) {
      return new M1(reader);
    }
  }

  static final class M3F extends ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.Event> {
    @Override
    public org.jetbrains.jsonProtocol.ProtocolMetaModel.Event read(org.jetbrains.io.JsonReaderEx reader) {
      return new M3(reader);
    }
  }

  static final class M7F extends ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.StandaloneType> {
    @Override
    public org.jetbrains.jsonProtocol.ProtocolMetaModel.StandaloneType read(org.jetbrains.io.JsonReaderEx reader) {
      return new M7(reader);
    }
  }

  static final class M2F extends ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.Domain> {
    @Override
    public org.jetbrains.jsonProtocol.ProtocolMetaModel.Domain read(org.jetbrains.io.JsonReaderEx reader) {
      return new M2(reader);
    }
  }

}