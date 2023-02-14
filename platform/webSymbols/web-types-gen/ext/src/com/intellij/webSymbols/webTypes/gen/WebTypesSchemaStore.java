package com.intellij.webSymbols.webTypes.gen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jsonschema2pojo.Schema;
import org.jsonschema2pojo.SchemaStore;

import java.net.URI;

public class WebTypesSchemaStore extends SchemaStore {

  public synchronized Schema createFakeSchema(URI uri, ObjectNode root) {
    Schema result = new Schema(uri, root, null);
    schemas.put(uri, result);
    return result;
  }

  @Override
  public synchronized Schema create(URI id, String refFragmentPathDelimiters) {
    if (!schemas.containsKey(id)) {
      URI baseId = removeFragment(id);
      Schema baseSchema = schemas.get(baseId);
      if (baseSchema == null) {
        JsonNode baseContent = contentResolver.resolve(baseId);
        baseSchema = new Schema(baseId, baseContent, null);
      }

      if (id.toString().contains("#")) {
        JsonNode childContent = fragmentResolver.resolve(baseSchema.getContent(), '#' + id.getFragment(), refFragmentPathDelimiters);
        schemas.put(id, new Schema(id, childContent, baseSchema));
      } else {
        schemas.put(id, baseSchema);
      }
    }

    return schemas.get(id);
  }

}
