// Generated source
package org.jetbrains.jsonProtocol;

import org.jetbrains.annotations.NotNull;

import static org.jetbrains.jsonProtocol.JsonReaders.nextList;
import static org.jetbrains.jsonProtocol.JsonReaders.readObjectArray;

public final class ProtocolSchemaReaderImpl implements org.jetbrains.jsonProtocol.ProtocolSchemaReader {
  @Override
  public org.jetbrains.jsonProtocol.ProtocolMetaModel.Root parseRoot(org.jetbrains.io.JsonReaderEx reader) {
    return new M0(reader,  null);
  }

  private static final class M0 implements org.jetbrains.jsonProtocol.ProtocolMetaModel.Root {
    private java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Domain> _domains;
    private org.jetbrains.jsonProtocol.ProtocolMetaModel.Version _version;

    M0(org.jetbrains.io.JsonReaderEx reader, String name) {
      if (name == null) {
        if (reader.hasNext() && reader.beginObject().hasNext()) {
          name = reader.nextName();
        }
        else {
          return;
        }
      }

      do {
        if (name.equals("domains")) {
          _domains = readObjectArray(reader, new M2F());
        }
        else if (name.equals("version")) {
          _version = new M1(reader, null);
        }
        else {
          reader.skipValue();
        }
      }
      while ((name = reader.nextNameOrNull()) != null);

      reader.endObject();
    }

    @NotNull
    @Override
    public java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Domain> domains() {
      return _domains;
    }

    @Override
    public org.jetbrains.jsonProtocol.ProtocolMetaModel.Version version() {
      return _version;
    }
  }

  private static final class M1 implements org.jetbrains.jsonProtocol.ProtocolMetaModel.Version {
    private String _major;
    private String _minor;

    M1(org.jetbrains.io.JsonReaderEx reader, String name) {
      if (name == null) {
        if (reader.hasNext() && reader.beginObject().hasNext()) {
          name = reader.nextName();
        }
        else {
          return;
        }
      }

      do {
        if (name.equals("major")) {
          _major = reader.nextString();
        }
        else if (name.equals("minor")) {
          _minor = reader.nextString();
        }
        else {
          reader.skipValue();
        }
      }
      while ((name = reader.nextNameOrNull()) != null);

      reader.endObject();
    }

    @NotNull
    @Override
    public java.lang.String major() {
      return _major;
    }

    @NotNull
    @Override
    public java.lang.String minor() {
      return _minor;
    }
  }

  private static final class M2 implements org.jetbrains.jsonProtocol.ProtocolMetaModel.Domain {
    private java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Command> _commands;
    private String _description;
    private String _domain;
    private java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Event> _events;
    private boolean _hidden;
    private java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.StandaloneType> _types;

    M2(org.jetbrains.io.JsonReaderEx reader, String name) {
      if (name == null) {
        if (reader.hasNext() && reader.beginObject().hasNext()) {
          name = reader.nextName();
        }
        else {
          return;
        }
      }

      do {
        if (name.equals("commands")) {
          _commands = readObjectArray(reader, new M3F());
        }
        else if (name.equals("description")) {
          _description = reader.nextNullableString();
        }
        else if (name.equals("domain")) {
          _domain = reader.nextString();
        }
        else if (name.equals("events")) {
          _events = readObjectArray(reader, new M5F());
        }
        else if (name.equals("hidden")) {
          _hidden = reader.nextBoolean();
        }
        else if (name.equals("types")) {
          _types = readObjectArray(reader, new M6F());
        }
        else {
          reader.skipValue();
        }
      }
      while ((name = reader.nextNameOrNull()) != null);

      reader.endObject();
    }

    @NotNull
    @Override
    public java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Command> commands() {
      return _commands;
    }

    @Override
    public java.lang.String description() {
      return _description;
    }

    @NotNull
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

  private static final class M3 implements org.jetbrains.jsonProtocol.ProtocolMetaModel.Command {
    private boolean _async;
    private String _description;
    private boolean _hidden;
    private String _name;
    private java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter> _parameters;
    private java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter> _returns;

    M3(org.jetbrains.io.JsonReaderEx reader, String name) {
      if (name == null) {
        if (reader.hasNext() && reader.beginObject().hasNext()) {
          name = reader.nextName();
        }
        else {
          return;
        }
      }

      do {
        if (name.equals("async")) {
          _async = reader.nextBoolean();
        }
        else if (name.equals("description")) {
          _description = reader.nextNullableString();
        }
        else if (name.equals("hidden")) {
          _hidden = reader.nextBoolean();
        }
        else if (name.equals("name")) {
          _name = reader.nextString();
        }
        else if (name.equals("parameters")) {
          _parameters = readObjectArray(reader, new M4F());
        }
        else if (name.equals("returns")) {
          _returns = readObjectArray(reader, new M4F());
        }
        else {
          reader.skipValue();
        }
      }
      while ((name = reader.nextNameOrNull()) != null);

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

    @NotNull
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

  private static final class M4 implements org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter {
    private String _description;
    private java.util.List<String> _getEnum;
    private boolean _hidden;
    private org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType _items;
    private String _name;
    private boolean _optional;
    private String _ref;
    private String _shortName;
    private String _type;

    M4(org.jetbrains.io.JsonReaderEx reader, String name) {
      if (name == null) {
        if (reader.hasNext() && reader.beginObject().hasNext()) {
          name = reader.nextName();
        }
        else {
          return;
        }
      }

      do {
        if (name.equals("description")) {
          _description = reader.nextNullableString();
        }
        else if (name.equals("enum")) {
          _getEnum = nextList(reader);
        }
        else if (name.equals("hidden")) {
          _hidden = reader.nextBoolean();
        }
        else if (name.equals("items")) {
          _items = new M7(reader, null);
        }
        else if (name.equals("name")) {
          _name = reader.nextString();
        }
        else if (name.equals("optional")) {
          _optional = reader.nextBoolean();
        }
        else if (name.equals("$ref")) {
          _ref = reader.nextNullableString();
        }
        else if (name.equals("shortName")) {
          _shortName = reader.nextNullableString();
        }
        else if (name.equals("type")) {
          _type = reader.nextNullableString();
        }
        else {
          reader.skipValue();
        }
      }
      while ((name = reader.nextNameOrNull()) != null);

      reader.endObject();
    }

    @Override
    public java.lang.String description() {
      return _description;
    }

    @Override
    public java.util.List<java.lang.String> getEnum() {
      return _getEnum;
    }

    @Override
    public boolean hidden() {
      return _hidden;
    }

    @Override
    public org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType items() {
      return _items;
    }

    @NotNull
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
      return _ref;
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

  private static final class M5 implements org.jetbrains.jsonProtocol.ProtocolMetaModel.Event {
    private String _description;
    private boolean _hidden;
    private String _name;
    private java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter> _parameters;

    M5(org.jetbrains.io.JsonReaderEx reader, String name) {
      if (name == null) {
        if (reader.hasNext() && reader.beginObject().hasNext()) {
          name = reader.nextName();
        }
        else {
          return;
        }
      }

      do {
        if (name.equals("description")) {
          _description = reader.nextNullableString();
        }
        else if (name.equals("hidden")) {
          _hidden = reader.nextBoolean();
        }
        else if (name.equals("name")) {
          _name = reader.nextString();
        }
        else if (name.equals("parameters")) {
          _parameters = readObjectArray(reader, new M4F());
        }
        else {
          reader.skipValue();
        }
      }
      while ((name = reader.nextNameOrNull()) != null);

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

    @NotNull
    @Override
    public java.lang.String name() {
      return _name;
    }

    @Override
    public java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter> parameters() {
      return _parameters;
    }
  }

  private static final class M6 implements org.jetbrains.jsonProtocol.ProtocolMetaModel.StandaloneType {
    private String _description;
    private java.util.List<String> _getEnum;
    private boolean _hidden;
    private String _id;
    private org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType _items;
    private java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.ObjectProperty> _properties;
    private String _type;

    M6(org.jetbrains.io.JsonReaderEx reader, String name) {
      if (name == null) {
        if (reader.hasNext() && reader.beginObject().hasNext()) {
          name = reader.nextName();
        }
        else {
          return;
        }
      }

      do {
        if (name.equals("description")) {
          _description = reader.nextNullableString();
        }
        else if (name.equals("enum")) {
          _getEnum = nextList(reader);
        }
        else if (name.equals("hidden")) {
          _hidden = reader.nextBoolean();
        }
        else if (name.equals("id")) {
          _id = reader.nextString();
        }
        else if (name.equals("items")) {
          _items = new M7(reader, null);
        }
        else if (name.equals("properties")) {
          _properties = readObjectArray(reader, new M8F());
        }
        else if (name.equals("type")) {
          _type = reader.nextString();
        }
        else {
          reader.skipValue();
        }
      }
      while ((name = reader.nextNameOrNull()) != null);

      reader.endObject();
    }

    @Override
    public java.lang.String description() {
      return _description;
    }

    @Override
    public java.util.List<java.lang.String> getEnum() {
      return _getEnum;
    }

    @Override
    public boolean hidden() {
      return _hidden;
    }

    @NotNull
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

    @NotNull
    @Override
    public java.lang.String type() {
      return _type;
    }
  }

  private static final class M7 implements org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType {
    private String _description;
    private java.util.List<String> _getEnum;
    private org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType _items;
    private boolean _optional;
    private java.util.List<org.jetbrains.jsonProtocol.ProtocolMetaModel.ObjectProperty> _properties;
    private String _ref;
    private String _type;

    M7(org.jetbrains.io.JsonReaderEx reader, String name) {
      if (name == null) {
        if (reader.hasNext() && reader.beginObject().hasNext()) {
          name = reader.nextName();
        }
        else {
          return;
        }
      }

      do {
        if (name.equals("description")) {
          _description = reader.nextNullableString();
        }
        else if (name.equals("enum")) {
          _getEnum = nextList(reader);
        }
        else if (name.equals("items")) {
          _items = new M7(reader, null);
        }
        else if (name.equals("optional")) {
          _optional = reader.nextBoolean();
        }
        else if (name.equals("properties")) {
          _properties = readObjectArray(reader, new M8F());
        }
        else if (name.equals("$ref")) {
          _ref = reader.nextNullableString();
        }
        else if (name.equals("type")) {
          _type = reader.nextNullableString();
        }
        else {
          reader.skipValue();
        }
      }
      while ((name = reader.nextNameOrNull()) != null);

      reader.endObject();
    }

    @Override
    public java.lang.String description() {
      return _description;
    }

    @Override
    public java.util.List<java.lang.String> getEnum() {
      return _getEnum;
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
      return _ref;
    }

    @Override
    public java.lang.String type() {
      return _type;
    }
  }

  private static final class M8 implements org.jetbrains.jsonProtocol.ProtocolMetaModel.ObjectProperty {
    private String _description;
    private java.util.List<String> _getEnum;
    private boolean _hidden;
    private org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType _items;
    private String _name;
    private boolean _optional;
    private String _ref;
    private String _shortName;
    private String _type;

    M8(org.jetbrains.io.JsonReaderEx reader, String name) {
      if (name == null) {
        if (reader.hasNext() && reader.beginObject().hasNext()) {
          name = reader.nextName();
        }
        else {
          return;
        }
      }

      do {
        if (name.equals("description")) {
          _description = reader.nextNullableString();
        }
        else if (name.equals("enum")) {
          _getEnum = nextList(reader);
        }
        else if (name.equals("hidden")) {
          _hidden = reader.nextBoolean();
        }
        else if (name.equals("items")) {
          _items = new M7(reader, null);
        }
        else if (name.equals("name")) {
          _name = reader.nextString();
        }
        else if (name.equals("optional")) {
          _optional = reader.nextBoolean();
        }
        else if (name.equals("$ref")) {
          _ref = reader.nextNullableString();
        }
        else if (name.equals("shortName")) {
          _shortName = reader.nextNullableString();
        }
        else if (name.equals("type")) {
          _type = reader.nextNullableString();
        }
        else {
          reader.skipValue();
        }
      }
      while ((name = reader.nextNameOrNull()) != null);

      reader.endObject();
    }

    @Override
    public java.lang.String description() {
      return _description;
    }

    @Override
    public java.util.List<java.lang.String> getEnum() {
      return _getEnum;
    }

    @Override
    public boolean hidden() {
      return _hidden;
    }

    @Override
    public org.jetbrains.jsonProtocol.ProtocolMetaModel.ArrayItemType items() {
      return _items;
    }

    @NotNull
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
      return _ref;
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

  private static final class M2F extends ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.Domain> {
    @Override
    public org.jetbrains.jsonProtocol.ProtocolMetaModel.Domain read(org.jetbrains.io.JsonReaderEx reader) {
      return new M2(reader, null);
    }
  }

  private static final class M3F extends ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.Command> {
    @Override
    public org.jetbrains.jsonProtocol.ProtocolMetaModel.Command read(org.jetbrains.io.JsonReaderEx reader) {
      return new M3(reader, null);
    }
  }

  private static final class M5F extends ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.Event> {
    @Override
    public org.jetbrains.jsonProtocol.ProtocolMetaModel.Event read(org.jetbrains.io.JsonReaderEx reader) {
      return new M5(reader, null);
    }
  }

  private static final class M6F extends ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.StandaloneType> {
    @Override
    public org.jetbrains.jsonProtocol.ProtocolMetaModel.StandaloneType read(org.jetbrains.io.JsonReaderEx reader) {
      return new M6(reader, null);
    }
  }

  private static final class M4F extends ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter> {
    @Override
    public org.jetbrains.jsonProtocol.ProtocolMetaModel.Parameter read(org.jetbrains.io.JsonReaderEx reader) {
      return new M4(reader, null);
    }
  }

  private static final class M8F extends ObjectFactory<org.jetbrains.jsonProtocol.ProtocolMetaModel.ObjectProperty> {
    @Override
    public org.jetbrains.jsonProtocol.ProtocolMetaModel.ObjectProperty read(org.jetbrains.io.JsonReaderEx reader) {
      return new M8(reader, null);
    }
  }
}