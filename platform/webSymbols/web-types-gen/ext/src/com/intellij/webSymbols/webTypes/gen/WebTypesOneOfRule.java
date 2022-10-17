package com.intellij.webSymbols.webTypes.gen;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.codemodel.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jsonschema2pojo.Jsonschema2Pojo;
import org.jsonschema2pojo.Schema;
import org.jsonschema2pojo.exception.GenerationException;
import org.jsonschema2pojo.rules.Rule;
import org.jsonschema2pojo.rules.RuleFactory;
import org.jsonschema2pojo.util.SerializableHelper;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.webSymbols.webTypes.gen.WebTypesObjectRule.addInterfaces;
import static org.apache.commons.lang3.StringUtils.contains;
import static org.apache.commons.lang3.StringUtils.split;

public class WebTypesOneOfRule implements Rule<JPackage, JType> {

  private final WebTypesRuleFactory ruleFactory;

  private static final Map<String, List<String>> tokenTypeMap = new HashMap<>();

  static {
    tokenTypeMap.put("array", Collections.singletonList("START_ARRAY"));
    tokenTypeMap.put("object", Collections.singletonList("START_OBJECT"));
    tokenTypeMap.put("string", Collections.singletonList("VALUE_STRING"));
    tokenTypeMap.put("integer", Collections.singletonList("VALUE_NUMBER_INT"));
    tokenTypeMap.put("number", Arrays.asList("VALUE_NUMBER_INT", "VALUE_NUMBER_FLOAT"));
    tokenTypeMap.put("boolean", Arrays.asList("VALUE_TRUE", "VALUE_FALSE"));
  }

  protected WebTypesOneOfRule(WebTypesRuleFactory ruleFactory) {
    this.ruleFactory = ruleFactory;
  }

  @Override
  public JType apply(String nodeName,
                     JsonNode node,
                     JsonNode parent,
                     JPackage _package,
                     Schema schema) {
    JsonNode oneOf = node.get("oneOf");
    if (oneOf != null && oneOf.isArray() && oneOf.size() > 1
        && node.get("anyOf") == null && node.get("allOf") == null) {
      if (schema.getJavaType() != null) {
        return schema.getJavaType();
      }
      JsonNode lenientDeserializerNode = node.get("javaLenientDeserialize");
      boolean lenientDeserialize = lenientDeserializerNode != null && lenientDeserializerNode.asBoolean(false);
      Map<String, List<ResolvedType>> resolved = preprocess((ArrayNode)oneOf, schema);

      if (resolved.entrySet().stream().anyMatch(entry -> !"object".equals(entry.getKey()) && entry.getValue().size() > 1)) {
        ruleFactory.getLogger().error("Only one kind of non-object type can be specified within oneOf. Error for : " + nodeName);
        return null;
      }
      if (resolved.containsKey(null)) {
        ruleFactory.getLogger().error("One of oneOf branches has unspecified type. Error for : " + nodeName);
        return null;
      }

      JClass objectsSuperType = null;
      JDefinedClass wrapperClass = null;
      if (resolved.keySet().size() > 1) {
        wrapperClass = createClass(nodeName, node, _package, false);
        Map<String, Object> map = new HashMap<>();
        map.put("tokens", new HashSet<>(resolved.keySet()));
        wrapperClass.metadata = map;
        schema.setJavaTypeIfEmpty(wrapperClass);
      }

      List<ResolvedType> objects = resolved.get("object");
      if (objects.size() == 1) {
        ResolvedType obj = objects.get(0);
        objectsSuperType = (JClass)obj.generate(ruleFactory, nodeName + "-object", oneOf, _package);
        schema.setJavaTypeIfEmpty(objectsSuperType);
      }
      else if (objects.size() > 1) {
        String determinantPropertyName = findDeterminantField(objects);
        final JDefinedClass superType;
        if (determinantPropertyName != null) {
          // Enum based deserialization
          superType = createClass(nodeName + "-base", node, _package, true);
          schema.setJavaTypeIfEmpty(superType);
          generateEnumClass(nodeName, schema, objects, determinantPropertyName, superType);
          Map<String, JType> subTypes =
            generateEnumBasedSubTypes(nodeName, oneOf, objects, determinantPropertyName, superType, _package);
          annotateWithEnumBasedSubtypes(superType, subTypes, determinantPropertyName);
        }
        else if (suitableForDeductionBased(objects)) {
          // Deduction based deserialization
          superType = createClass(nodeName + "-base", node, _package, true);
          schema.setJavaTypeIfEmpty(superType);
          List<JType> subTypes = generateDeductionBasedSubTypes(nodeName, oneOf, objects, superType, _package);
          annotateWithDeductionBasedSubtypes(superType, subTypes);
        }
        else {
          ruleFactory.getLogger().error("No way to deduct object deserialization target through required properties: " + nodeName);
          return null;
        }
        objectsSuperType = superType;
      }
      Map<String, JClass> generatedTypes = new LinkedHashMap<>();
      for (Map.Entry<String, List<ResolvedType>> listEntry : resolved.entrySet()) {
        if (listEntry.getKey().equals("object")) {
          generatedTypes.put("object", objectsSuperType);
        }
        else {
          generatedTypes.put(listEntry.getKey(), (JClass)listEntry.getValue().get(0).generate(ruleFactory, nodeName, oneOf, wrapperClass));
        }
      }
      if (wrapperClass != null) {
        if (generatedTypes.size() == 2 && generatedTypes.get("array") != null) {
          JType arrayType = generatedTypes.get("array");
          Pair<JType, JClass> arrayItemType = unpackArrayItemClass(arrayType);
          if (arrayItemType != null) {
            Map.Entry<String, List<ResolvedType>> entry = resolved.entrySet().stream()
              .filter(e -> !e.getKey().equals("array")).findFirst().orElseThrow();
            JType itemType;
            if (entry.getKey().equals("object")) {
              itemType = objectsSuperType;
            }
            else {
              itemType = entry.getValue().get(0).generate(ruleFactory, nodeName, oneOf, wrapperClass);
            }
            if (itemType != null && itemType.equals(arrayItemType.getRight())) {
              generateDeserializeIntoArray(wrapperClass, entry.getKey(), arrayItemType, lenientDeserialize);
              return wrapperClass;
            }
          }
        }
        generateDeserializeIntoValueProperty(wrapperClass, generatedTypes, node);
        return wrapperClass;
      }
      return schema.getJavaType();
    }
    return null;
  }

  private void generateDeserializeIntoValueProperty(JDefinedClass wrapperClass, Map<String, JClass> types, JsonNode node) {
    JCodeModel model = wrapperClass.owner();

    String comment = "Type: {@code " + types.values().stream().map(it -> it.name()).collect(Collectors.joining(" | ")) + "}";

    boolean isIncludeGetters = ruleFactory.getGenerationConfig().isIncludeGetters();
    boolean isIncludeSetters = ruleFactory.getGenerationConfig().isIncludeSetters();

    int accessModifier = isIncludeGetters || isIncludeSetters ? JMod.PRIVATE : JMod.PUBLIC;
    JType propertyType = model.ref(Object.class);
    String propertyName = "value";
    JFieldVar field = wrapperClass.field(accessModifier, propertyType, propertyName);
    field.javadoc().add(comment);

    if (isIncludeGetters) {
      JMethod getter = wrapperClass.method(JMod.PUBLIC, propertyType, "getValue");
      getter.javadoc().add(comment);
      JBlock body = getter.body();
      body._return(field);
    }

    if (isIncludeSetters) {
      JMethod setter = wrapperClass.method(JMod.PUBLIC, void.class, "setValue");
      setter.javadoc().add(comment);
      JVar param = setter.param(field.type(), field.name());
      JBlock body = setter.body();
      body.assign(JExpr._this().ref(field), param);
    }

    if (ruleFactory.getGenerationConfig().isIncludeToString()) {
      addToString(wrapperClass);
    }

    if (ruleFactory.getGenerationConfig().isIncludeHashcodeAndEquals()) {
      addHashCode(wrapperClass, node);
      addEquals(wrapperClass, node);
    }

    if (ruleFactory.getGenerationConfig().isSerializable()) {
      SerializableHelper.addSerializableSupport(wrapperClass);
    }

    if (node.has("javaInterfaces")) {
      addInterfaces(wrapperClass, node.get("javaInterfaces"));
    }

    JBlock statements = attachDeserializerTo(wrapperClass);

    statements.decl(0, wrapperClass, "result", JExpr._new(wrapperClass));
    statements.decl(0, model.ref(JsonToken.class), "token", JExpr.invoke(JExpr.ref("parser"), "currentToken"));

    JBlock block = statements;
    for (Map.Entry<String, JClass> entry : types.entrySet()) {
      block = createDeserializeFor(block, entry.getKey(), entry.getValue());
    }
    block.invoke(JExpr.ref("deserializationContext"), "handleUnexpectedToken")
      .arg(JExpr.dotclass(model.ref(Object.class)))
      .arg(JExpr.ref("parser"));
    statements._return(JExpr.ref("result"));
  }

  private static JBlock createDeserializeFor(JBlock block, String jsonType, JClass javaType) {
    JCodeModel model = javaType.owner();
    JConditional _if = block._if(createTokenCondition(model, jsonType));
    _if._then().assign(JExpr.ref(JExpr.ref("result"), "value"), createReadValueAs(javaType));
    return _if._else();
  }

  private static JExpression createTokenCondition(JCodeModel model, String... jsonTypes) {
    List<String> tokenKinds = new ArrayList<>();
    for (String jsonType : jsonTypes) {
      tokenKinds.addAll(tokenTypeMap.get(jsonType));
    }
    JExpression condition = null;
    for (String token : tokenKinds) {
      JExpression tokenCondition = JExpr.ref("token").eq(model.ref(JsonToken.class).staticRef(token));
      if (condition == null) {
        condition = tokenCondition;
      }
      else {
        condition = condition.cor(tokenCondition);
      }
    }
    return condition;
  }

  private static JBlock attachDeserializerTo(JDefinedClass pojo) {
    final JDefinedClass deserializer;
    try {
      deserializer = pojo._class(JMod.STATIC | JMod.PUBLIC, "MyDeserializer");
    }
    catch (JClassAlreadyExistsException e) {
      throw new GenerationException(e);
    }
    deserializer._extends(pojo.owner().ref(JsonDeserializer.class).narrow(pojo));

    JAnnotationUse annotation = pojo.annotate(JsonDeserialize.class);
    annotation.param("using", deserializer);

    JMethod deserialize = deserializer.method(JMod.PUBLIC, pojo, "deserialize");
    deserialize.annotate(Override.class);
    deserialize.param(JsonParser.class, "parser");
    deserialize.param(DeserializationContext.class, "deserializationContext");
    deserialize._throws(IOException.class);

    return deserialize.body();
  }

  private static void generateDeserializeIntoArray(JDefinedClass wrapperClass, String arrayItemJsonType,
                                                   Pair<JType, JClass> arrayItemType, boolean lenientDeserialize) {
    JCodeModel model = wrapperClass.owner();
    JClass jClass = arrayItemType.getValue();
    if (arrayItemType.getKey().equals(model.ref(List.class))) {
      wrapperClass._extends(model.ref(ArrayList.class).narrow(jClass));
    }
    else {
      wrapperClass._extends(model.ref(HashSet.class).narrow(jClass));
    }

    JBlock statements = attachDeserializerTo(wrapperClass);
    statements.decl(0, wrapperClass, "result", JExpr._new(wrapperClass));
    statements.decl(0, model.ref(JsonToken.class), "token", JExpr.invoke(JExpr.ref("parser"), "currentToken"));
    JConditional mainIf = statements._if(JExpr.ref("token").eq(model.ref(JsonToken.class).staticRef("START_ARRAY")));


    String[] jsonItemTypes = new String[]{arrayItemJsonType};
    if (jClass instanceof JDefinedClass) {
      Object metadata = ((JDefinedClass)jClass).metadata;
      if (metadata instanceof Map) {
        Object tokens = ((Map<?, ?>)metadata).get("tokens");
        if (tokens instanceof Set) {
          //noinspection unchecked
          jsonItemTypes = ((Set<String>)tokens).toArray(String[]::new);
        }
      }
    }

    JBlock whileBlock = mainIf._then()._while(JExpr.ref("parser").invoke("nextToken").ne(model.ref(JsonToken.class).staticRef("END_ARRAY")))
      .body();

    whileBlock.assign(JExpr.ref("token"), JExpr.invoke(JExpr.ref("parser"), "currentToken"));

    createAddToResultGuarded(whileBlock, jClass, jsonItemTypes, lenientDeserialize);

    createAddToResultGuarded(mainIf._else(), jClass, jsonItemTypes, lenientDeserialize);
    statements._return(JExpr.ref("result"));
  }

  private static void createAddToResultGuarded(JBlock block, JClass jClass, String[] jsonItemTypes,
                                               boolean lenientDeserialize) {
    JCodeModel model = jClass.owner();
    JConditional ifBlock = block._if(createTokenCondition(model, jsonItemTypes));
    createAddToResult(ifBlock._then(), jClass);
    JBlock errorBlock = ifBlock._else();
    if (lenientDeserialize) {
      errorBlock.invoke(JExpr.ref("parser"), "readValueAsTree");
    }
    else {
      errorBlock.invoke(JExpr.ref("deserializationContext"), "handleUnexpectedToken")
        .arg(jClass.dotclass())
        .arg(JExpr.ref("parser"));
    }
  }

  private static void createAddToResult(JBlock block, JClass itemClass) {
    block.invoke(JExpr.ref("result"), "add").arg(createReadValueAs(itemClass));
  }

  private static JInvocation createReadValueAs(JClass itemClass) {
    if (itemClass.getTypeParameters().isEmpty()) {
      return JExpr.ref("parser").invoke("readValueAs").arg(JExpr.dotclass(itemClass));
    }
    else {
      JInvocation inv = JExpr.ref("deserializationContext").invoke("getTypeFactory").invoke("constructParametricType")
        .arg(itemClass.dotclass());
      for (JClass param : itemClass.getTypeParameters()) {
        inv.arg(param.dotclass());
      }
      return JExpr.ref("parser").invoke("getCodec").invoke("readValue").arg(JExpr.ref("parser")).arg(inv);
    }
  }

  private static Pair<JType, JClass> unpackArrayItemClass(JType arrayType) {
    if (!(arrayType instanceof JClass)) return null;

    JClass cls = (JClass)arrayType;
    JClass baseClass = cls.erasure();
    if (!baseClass.equals(arrayType.owner().ref(List.class)) && !baseClass.equals(arrayType.owner().ref(Set.class))) return null;

    List<JClass> parameters = cls.getTypeParameters();
    if (parameters == null || parameters.size() != 1) return null;

    return Pair.of(baseClass, parameters.get(0));
  }

  private Map<String, List<ResolvedType>> preprocess(ArrayNode oneOf, Schema schema) {
    Map<String, List<ResolvedType>> result = new LinkedHashMap<>();
    int index = 0;
    for (JsonNode child : oneOf) {
      Schema childSchema = ruleFactory.resolveSchemaRef(schema, "oneOf/" + (index++));
      ResolvedType resolved = resolveRefs(null, child, childSchema);
      String type = getTypeName(resolved.node);
      result.computeIfAbsent(type, (k) -> new ArrayList<>()).add(resolved);
    }
    return result;
  }

  private static String getTypeName(JsonNode node) {
    if (node.path("properties").size() > 0 || node.path("oneOf").size() > 0) {
      return "object";
    }
    if (node.has("type") && node.get("type").isArray() && node.get("type").size() > 0) {
      for (JsonNode jsonNode : node.get("type")) {
        String typeName = jsonNode.asText();
        if (!typeName.equals("null")) {
          return typeName;
        }
      }
    }

    if (node.has("type") && node.get("type").isTextual()) {
      return node.get("type").asText();
    }

    return null;
  }

  private ResolvedType resolveRefs(String name, JsonNode node, Schema schema) {
    if (node.has("$ref")) {
      final String nameFromRef = nameFromRef(node.get("$ref").asText());
      Schema childSchema = ruleFactory.getSchemaStore()
        .create(schema, node.get("$ref").asText(), ruleFactory.getGenerationConfig().getRefFragmentPathDelimiters());
      JsonNode schemaNode = childSchema.getContent();
      return resolveRefs(nameFromRef != null ? nameFromRef : name, schemaNode, childSchema);
    }
    schema = schema.deriveChildSchema(node);
    return new ResolvedType(name, node, schema);
  }

  private static boolean suitableForDeductionBased(List<ResolvedType> objects) {
    List<Set<String>> requiredFields = new ArrayList<>();
    Set<String> defaultObjFields = null;
    for (ResolvedType obj : objects) {
      Set<String> localRequired = getRequiredFields(obj.node);
      if (localRequired.isEmpty()) {
        if (defaultObjFields != null) {
          return false;
        }
        defaultObjFields = new HashSet<>();
        JsonNode properties = obj.node.get("properties");
        if (properties != null) {
          for (Iterator<String> it = properties.fieldNames(); it.hasNext(); ) {
            defaultObjFields.add(it.next());
          }
        }
      }
      else {
        requiredFields.add(localRequired);
      }
    }
    for (int i = 0; i < requiredFields.size(); i++) {
      Set<String> a = requiredFields.get(0);
      if (defaultObjFields != null && defaultObjFields.stream().anyMatch(a::contains)) {
        return false;
      }
      for (int j = i + 1; j < requiredFields.size(); j++) {
        Set<String> b = requiredFields.get(1);
        if (a.containsAll(b) || b.containsAll(a)) {
          return false;
        }
      }
    }
    return true;
  }

  private static Set<String> getRequiredFields(JsonNode node) {
    JsonNode requiredList = node.get("required");
    if (requiredList == null || (!requiredList.isArray() && requiredList.size() == 0)) return Collections.emptySet();
    Set<String> result = new TreeSet<>();
    for (JsonNode element : requiredList) {
      if (element.isTextual()) {
        result.add(element.asText());
      }
    }
    return result;
  }

  private List<JType> generateDeductionBasedSubTypes(String baseName, JsonNode parent, List<ResolvedType> objects,
                                                     JDefinedClass superType, JPackage pkg) {
    List<JType> result = new ArrayList<>();
    for (ResolvedType obj : objects) {
      Set<String> requiredFields = getRequiredFields(obj.node);
      String subTypeName = baseName + "-" + (obj.name != null
                                             ? obj.name
                                             : (requiredFields.isEmpty() ? "default" : StringUtils.join(requiredFields, "-")));
      JDefinedClass subType = (JDefinedClass)ruleFactory.getObjectRule().apply(subTypeName, obj.node, parent, pkg, obj.schema);
      subType._extends(superType);
      result.add(subType);
    }
    return result;
  }

  private Map<String, JType> generateEnumBasedSubTypes(String baseName, JsonNode parent, List<ResolvedType> objects, String propertyName,
                                                       JDefinedClass superType, JPackage pkg) {
    Map<String, JType> result = new TreeMap<>();
    for (ResolvedType obj : objects) {
      ObjectNode node = (ObjectNode)obj.node;
      JsonNode determinant = ((ObjectNode)node.path("properties")).remove(propertyName);
      String key = determinant.path("const").asText();
      node.set("title", JsonNodeFactory.instance.textNode(propertyName + " = " + key));
      String name = obj.name != null ? obj.name : baseName + "." + key;
      JDefinedClass subType = (JDefinedClass)ruleFactory.getObjectRule().apply(name, node, parent, pkg, obj.schema);
      subType._extends(superType);
      result.put(key, subType);
    }
    return result;
  }

  private static void annotateWithEnumBasedSubtypes(JDefinedClass jclass, Map<String, JType> subTypes, String propertyName) {
    JAnnotationUse jsonTypeInfo = jclass.annotate(JsonTypeInfo.class);
    jsonTypeInfo.param("use", JsonTypeInfo.Id.NAME);
    jsonTypeInfo.param("property", propertyName);
    jsonTypeInfo.param("visible", true);

    JAnnotationUse jsonSubTypes = jclass.annotate(JsonSubTypes.class);
    JAnnotationArrayMember subTypesList = jsonSubTypes.paramArray("value");
    for (Map.Entry<String, JType> entry : subTypes.entrySet()) {
      JAnnotationUse subtype = subTypesList.annotate(JsonSubTypes.Type.class);
      subtype.param("name", entry.getKey());
      subtype.param("value", entry.getValue());
    }
  }

  private static void annotateWithDeductionBasedSubtypes(JDefinedClass jclass, List<JType> subTypes) {
    JAnnotationUse jsonTypeInfo = jclass.annotate(JsonTypeInfo.class);
    jsonTypeInfo.param("use", JsonTypeInfo.Id.DEDUCTION);

    JAnnotationUse jsonSubTypes = jclass.annotate(JsonSubTypes.class);
    JAnnotationArrayMember subTypesList = jsonSubTypes.paramArray("value");
    for (JType value : subTypes) {
      JAnnotationUse subtype = subTypesList.annotate(JsonSubTypes.Type.class);
      subtype.param("value", value);
    }
  }

  private JDefinedClass createClass(String nodeName, JsonNode node, JPackage _package, boolean isAbstract) {
    final JDefinedClass result;
    try {
      result = _package._class(isAbstract ? JMod.PUBLIC | JMod.ABSTRACT : JMod.PUBLIC,
                               ruleFactory.getNameHelper().getUniqueClassName(nodeName, node, _package));
    }
    catch (JClassAlreadyExistsException e) {
      ruleFactory.getLogger().error(e.getMessage());
      throw new RuntimeException(e);
    }
    return result;
  }

  private void generateEnumClass(String nodeName,
                                 Schema schema,
                                 List<ResolvedType> objects,
                                 String determinantPropertyName,
                                 JDefinedClass superType) {
    Set<String> enumValues = findEnumValuesForOneOf(objects, determinantPropertyName);

    JsonNodeFactory factory = JsonNodeFactory.instance;
    ObjectNode root = factory.objectNode();
    ObjectNode properties = factory.objectNode();
    ObjectNode determinantProperty = factory.objectNode();
    ArrayNode enumValuesArray = factory.arrayNode(enumValues.size());
    root.set("properties", properties);
    root.set("additionalProperties", factory.booleanNode(false));
    properties.set(determinantPropertyName, determinantProperty);
    determinantProperty.set("type", factory.textNode("string"));
    determinantProperty.set("enum", enumValuesArray);
    for (String enumValue : enumValues) {
      enumValuesArray.add(enumValue);
    }
    Schema superTypeSchema = ruleFactory.getSchemaStore().createFakeSchema(
      URI.create("fake://" + superType.fullName() + "$" + determinantPropertyName + ".json"), root);
    ruleFactory.getPropertiesRule().apply(nodeName, properties, root, superType, superTypeSchema);
  }

  private Set<String> findEnumValuesForOneOf(List<ResolvedType> objects, String determinantField) {
    Set<String> result = new TreeSet<>();
    for (ResolvedType obj : objects) {
      String value = obj.node.get("properties").get(determinantField).get("const").asText(null);
      if (value == null) {
        ruleFactory.getLogger().error("No value for oneOf: " + determinantField);
        throw new NullPointerException();
      }
      if (!result.add(value)) {
        ruleFactory.getLogger().error("Duplicated value for oneOf: " + value);
        throw new IllegalStateException("Duplicated value for oneOf: " + value);
      }
    }
    return result;
  }

  private static String findDeterminantField(List<ResolvedType> objects) {
    String fieldName = null;
    for (ResolvedType type : objects) {
      JsonNode properties = type.node.get("properties");
      if (properties == null || !properties.isObject()) return null;
      for (Iterator<Map.Entry<String, JsonNode>> it = properties.fields(); it.hasNext(); ) {
        Map.Entry<String, JsonNode> property = it.next();
        if (isConstString(property.getValue())) {
          if (fieldName == null) {
            fieldName = property.getKey();
          }
          else if (!property.getKey().equals(fieldName)) {
            return null;
          }
        }
      }
    }
    return fieldName;
  }

  private static boolean isConstString(JsonNode value) {
    return value.isObject()
           && value.path("const").asText(null) != null
           && value.path("type").asText("").equals("string");
  }

  private String nameFromRef(String ref) {
    if ("#".equals(ref)) {
      return null;
    }

    String nameFromRef;
    if (!contains(ref, "#")) {
      nameFromRef = Jsonschema2Pojo.getNodeName(ref, ruleFactory.getGenerationConfig());
    }
    else {
      String[] nameParts = split(ref, "/\\#");
      nameFromRef = nameParts[nameParts.length - 1];
    }

    return URLDecoder.decode(nameFromRef, StandardCharsets.UTF_8);
  }


  private void addToString(JDefinedClass jclass) {
    Map<String, JFieldVar> fields = jclass.fields();
    JMethod toString = jclass.method(JMod.PUBLIC, String.class, "toString");
    //noinspection SSBasedInspection
    Set<String> excludes = new HashSet<>(Arrays.asList(ruleFactory.getGenerationConfig().getToStringExcludes()));

    JBlock body = toString.body();

    // The following toString implementation roughly matches the commons ToStringBuilder for
    // backward compatibility
    JClass stringBuilderClass = jclass.owner().ref(StringBuilder.class);
    JVar sb = body.decl(stringBuilderClass, "sb", JExpr._new(stringBuilderClass));

    // Write the header, e.g.: example.domain.MyClass@85e382a7[
    body.add(sb
               .invoke("append").arg(jclass.dotclass().invoke("getName"))
               .invoke("append").arg(JExpr.lit('@'))
               .invoke("append").arg(
        jclass.owner().ref(Integer.class).staticInvoke("toHexString").arg(
          jclass.owner().ref(System.class).staticInvoke("identityHashCode").arg(JExpr._this())))
               .invoke("append").arg(JExpr.lit('[')));

    // If this has a parent class, include its toString()
    if (!jclass._extends().fullName().equals(Object.class.getName())) {
      JVar baseLength = body.decl(jclass.owner().INT, "baseLength", sb.invoke("length"));
      JVar superString = body.decl(jclass.owner().ref(String.class), "superString", JExpr._super().invoke("toString"));

      JBlock superToStringBlock = body._if(superString.ne(JExpr._null()))._then();

      // If super.toString() is in the Clazz@2ee6529d[field=10] format, extract the fields
      // from the wrapper
      JVar contentStart = superToStringBlock.decl(jclass.owner().INT, "contentStart",
                                                  superString.invoke("indexOf").arg(JExpr.lit('[')));
      JVar contentEnd = superToStringBlock.decl(jclass.owner().INT, "contentEnd",
                                                superString.invoke("lastIndexOf").arg(JExpr.lit(']')));

      JConditional superToStringInnerConditional = superToStringBlock._if(
        contentStart.gte(JExpr.lit(0)).cand(contentEnd.gt(contentStart)));

      superToStringInnerConditional._then().add(
        sb.invoke("append")
          .arg(superString)
          .arg(contentStart.plus(JExpr.lit(1)))
          .arg(contentEnd));

      // Otherwise, just append super.toString()
      superToStringInnerConditional._else().add(sb.invoke("append").arg(superString));

      // Append a comma if needed
      body._if(sb.invoke("length").gt(baseLength))
        ._then().add(sb.invoke("append").arg(JExpr.lit(',')));
    }

    // For each included instance field, add to the StringBuilder in the field=value format
    for (JFieldVar fieldVar : fields.values()) {
      if (excludes.contains(fieldVar.name()) || (fieldVar.mods().getValue() & JMod.STATIC) == JMod.STATIC) {
        continue;
      }

      body.add(sb.invoke("append").arg(fieldVar.name()));
      body.add(sb.invoke("append").arg(JExpr.lit('=')));

      if (fieldVar.type().isPrimitive()) {
        body.add(sb.invoke("append").arg(JExpr.refthis(fieldVar.name())));
      }
      else if (fieldVar.type().isArray()) {
        // Only primitive arrays are supported
        if (!fieldVar.type().elementType().isPrimitive()) {
          throw new UnsupportedOperationException("Only primitive arrays are supported");
        }

        // Leverage Arrays.toString()
        body.add(sb.invoke("append")
                   .arg(JOp.cond(
                     JExpr.refthis(fieldVar.name()).eq(JExpr._null()),
                     JExpr.lit("<null>"),
                     jclass.owner().ref(Arrays.class).staticInvoke("toString")
                       .arg(JExpr.refthis(fieldVar.name()))
                       .invoke("replace").arg(JExpr.lit('[')).arg(JExpr.lit('{'))
                       .invoke("replace").arg(JExpr.lit(']')).arg(JExpr.lit('}'))
                       .invoke("replace").arg(JExpr.lit(", ")).arg(JExpr.lit(",")))));
      }
      else {
        body.add(sb.invoke("append")
                   .arg(JOp.cond(
                     JExpr.refthis(fieldVar.name()).eq(JExpr._null()),
                     JExpr.lit("<null>"),
                     JExpr.refthis(fieldVar.name()))));
      }

      body.add(sb.invoke("append").arg(JExpr.lit(',')));
    }

    // Add the trailer
    JConditional trailerConditional = body._if(
      sb.invoke("charAt").arg(sb.invoke("length").minus(JExpr.lit(1)))
        .eq(JExpr.lit(',')));

    trailerConditional._then().add(
      sb.invoke("setCharAt")
        .arg(sb.invoke("length").minus(JExpr.lit(1)))
        .arg(JExpr.lit(']')));

    trailerConditional._else().add(
      sb.invoke("append").arg(JExpr.lit(']')));


    body._return(sb.invoke("toString"));

    toString.annotate(Override.class);
  }

  private void addHashCode(JDefinedClass jclass, JsonNode node) {
    Map<String, JFieldVar> fields = removeFieldsExcludedFromEqualsAndHashCode(jclass.fields(), node);

    JMethod hashCode = jclass.method(JMod.PUBLIC, int.class, "hashCode");
    JBlock body = hashCode.body();
    JVar result = body.decl(jclass.owner().INT, "result", JExpr.lit(1));

    // Incorporate each non-excluded field in the hashCode calculation
    for (JFieldVar fieldVar : fields.values()) {
      if ((fieldVar.mods().getValue() & JMod.STATIC) == JMod.STATIC) {
        continue;
      }

      JFieldRef fieldRef = JExpr.refthis(fieldVar.name());

      JExpression fieldHash;
      if (fieldVar.type().isPrimitive()) {
        if ("long".equals(fieldVar.type().name())) {
          fieldHash = JExpr.cast(jclass.owner().INT, fieldRef.xor(fieldRef.shrz(JExpr.lit(32))));
        }
        else if ("boolean".equals(fieldVar.type().name())) {
          fieldHash = JOp.cond(fieldRef, JExpr.lit(1), JExpr.lit(0));
        }
        else if ("int".equals(fieldVar.type().name())) {
          fieldHash = fieldRef;
        }
        else if ("double".equals(fieldVar.type().name())) {
          JClass doubleClass = jclass.owner().ref(Double.class);
          JExpression longField = doubleClass.staticInvoke("doubleToLongBits").arg(fieldRef);
          fieldHash = JExpr.cast(jclass.owner().INT,
                                 longField.xor(longField.shrz(JExpr.lit(32))));
        }
        else if ("float".equals(fieldVar.type().name())) {
          fieldHash = jclass.owner().ref(Float.class).staticInvoke("floatToIntBits").arg(fieldRef);
        }
        else {
          fieldHash = JExpr.cast(jclass.owner().INT, fieldRef);
        }
      }
      else if (fieldVar.type().isArray()) {
        if (!fieldVar.type().elementType().isPrimitive()) {
          throw new UnsupportedOperationException("Only primitive arrays are supported");
        }

        fieldHash = jclass.owner().ref(Arrays.class).staticInvoke("hashCode").arg(fieldRef);
      }
      else {
        fieldHash = JOp.cond(fieldRef.eq(JExpr._null()), JExpr.lit(0), fieldRef.invoke("hashCode"));
      }

      body.assign(result, result.mul(JExpr.lit(31)).plus(fieldHash));
    }

    // Add super.hashCode()
    if (!jclass._extends().fullName().equals(Object.class.getName())) {
      body.assign(result, result.mul(JExpr.lit(31)).plus(JExpr._super().invoke("hashCode")));
    }

    body._return(result);
    hashCode.annotate(Override.class);
  }

  private Map<String, JFieldVar> removeFieldsExcludedFromEqualsAndHashCode(Map<String, JFieldVar> fields, JsonNode node) {
    Map<String, JFieldVar> filteredFields = new HashMap<>(fields);

    JsonNode properties = node.get("properties");

    if (properties != null) {
      if (node.has("excludedFromEqualsAndHashCode")) {
        JsonNode excludedArray = node.get("excludedFromEqualsAndHashCode");

        for (Iterator<JsonNode> iterator = excludedArray.elements(); iterator.hasNext(); ) {
          String excludedPropertyName = iterator.next().asText();
          JsonNode excludedPropertyNode = properties.get(excludedPropertyName);
          filteredFields.remove(ruleFactory.getNameHelper().getPropertyName(excludedPropertyName, excludedPropertyNode));
        }
      }

      for (Iterator<Map.Entry<String, JsonNode>> iterator = properties.fields(); iterator.hasNext(); ) {
        Map.Entry<String, JsonNode> entry = iterator.next();
        String propertyName = entry.getKey();
        JsonNode propertyNode = entry.getValue();

        if (propertyNode.has("excludedFromEqualsAndHashCode") &&
            propertyNode.get("excludedFromEqualsAndHashCode").asBoolean()) {
          filteredFields.remove(ruleFactory.getNameHelper().getPropertyName(propertyName, propertyNode));
        }
      }
    }

    return filteredFields;
  }

  private void addEquals(JDefinedClass jclass, JsonNode node) {
    Map<String, JFieldVar> fields = removeFieldsExcludedFromEqualsAndHashCode(jclass.fields(), node);

    JMethod equals = jclass.method(JMod.PUBLIC, boolean.class, "equals");
    JVar otherObject = equals.param(Object.class, "other");

    JBlock body = equals.body();

    body._if(otherObject.eq(JExpr._this()))._then()._return(JExpr.TRUE);
    body._if(otherObject._instanceof(jclass).eq(JExpr.FALSE))._then()._return(JExpr.FALSE);

    JVar rhsVar = body.decl(jclass, "rhs").init(JExpr.cast(jclass, otherObject));

    JExpression result = JExpr.lit(true);

    // First, check super.equals(other)
    if (!jclass._extends().fullName().equals(Object.class.getName())) {
      result = result.cand(JExpr._super().invoke("equals").arg(rhsVar));
    }

    // Chain the results of checking all other fields
    for (JFieldVar fieldVar : fields.values()) {
      if ((fieldVar.mods().getValue() & JMod.STATIC) == JMod.STATIC) {
        continue;
      }

      JFieldRef thisFieldRef = JExpr.refthis(fieldVar.name());
      JFieldRef otherFieldRef = JExpr.ref(rhsVar, fieldVar.name());
      JExpression fieldEquals;

      if (fieldVar.type().isPrimitive()) {
        if ("double".equals(fieldVar.type().name())) {
          JClass doubleClass = jclass.owner().ref(Double.class);
          fieldEquals = doubleClass.staticInvoke("doubleToLongBits").arg(thisFieldRef).eq(
            doubleClass.staticInvoke("doubleToLongBits").arg(otherFieldRef));
        }
        else if ("float".equals(fieldVar.type().name())) {
          JClass floatClass = jclass.owner().ref(Float.class);
          fieldEquals = floatClass.staticInvoke("floatToIntBits").arg(thisFieldRef).eq(
            floatClass.staticInvoke("floatToIntBits").arg(otherFieldRef));
        }
        else {
          fieldEquals = thisFieldRef.eq(otherFieldRef);
        }
      }
      else if (fieldVar.type().isArray()) {
        if (!fieldVar.type().elementType().isPrimitive()) {
          throw new UnsupportedOperationException("Only primitive arrays are supported");
        }

        fieldEquals = jclass.owner().ref(Arrays.class).staticInvoke("equals").arg(thisFieldRef).arg(otherFieldRef);
      }
      else {
        fieldEquals = thisFieldRef.eq(otherFieldRef).cor(
          thisFieldRef.ne(JExpr._null())
            .cand(thisFieldRef.invoke("equals").arg(otherFieldRef)));
      }

      // Chain the equality of this field with the previous comparisons
      result = result.cand(fieldEquals);
    }

    body._return(result);

    equals.annotate(Override.class);
  }

  private static class ResolvedType {
    public final String name;
    public final JsonNode node;
    public final Schema schema;

    ResolvedType(String name, JsonNode node, Schema schema) {
      this.name = name;
      this.node = node;
      this.schema = schema;
    }

    JType generate(RuleFactory ruleFactory, String defaultName, JsonNode parent, JClassContainer container) {
      if (schema.isGenerated()) {
        return schema.getJavaType();
      }
      else {
        return ruleFactory.getTypeRule().apply(name != null ? name : defaultName,
                                               node, parent, container, schema);
      }
    }
  }
}
