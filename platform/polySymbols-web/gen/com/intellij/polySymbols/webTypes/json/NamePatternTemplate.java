
package com.intellij.polySymbols.webTypes.json;

import java.util.List;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = NamePatternTemplate.MyDeserializer.class)
public class NamePatternTemplate {

    /**
     * Type: {@code String | List<NamePatternTemplate> | NamePatternBase}
     * 
     */
    private Object value;

    /**
     * Type: {@code String | List<NamePatternTemplate> | NamePatternBase}
     * 
     */
    public Object getValue() {
        return value;
    }

    /**
     * Type: {@code String | List<NamePatternTemplate> | NamePatternBase}
     * 
     */
    public void setValue(Object value) {
        this.value = value;
    }

    public static class MyDeserializer
        extends ValueDeserializer<NamePatternTemplate>
    {


        @Override
        public NamePatternTemplate deserialize(JsonParser parser, DeserializationContext deserializationContext)
        {
            NamePatternTemplate result = new NamePatternTemplate();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.VALUE_STRING) {
                result.value = parser.readValueAs(String.class);
            } else {
                if (token == JsonToken.START_ARRAY) {
                    result.value = deserializationContext.readValue(parser, deserializationContext.getTypeFactory().constructParametricType(List.class, NamePatternTemplate.class));
                } else {
                    if (token == JsonToken.START_OBJECT) {
                        result.value = parser.readValueAs(NamePatternBase.class);
                    } else {
                        deserializationContext.handleUnexpectedToken(Object.class, parser);
                    }
                }
            }
            return result;
        }

    }

}
