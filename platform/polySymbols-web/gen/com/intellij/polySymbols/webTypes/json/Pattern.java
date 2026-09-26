
package com.intellij.polySymbols.webTypes.json;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = Pattern.MyDeserializer.class)
public class Pattern {

    /**
     * Type: {@code String | PatternObject}
     * 
     */
    private Object value;

    /**
     * Type: {@code String | PatternObject}
     * 
     */
    public Object getValue() {
        return value;
    }

    /**
     * Type: {@code String | PatternObject}
     * 
     */
    public void setValue(Object value) {
        this.value = value;
    }

    public static class MyDeserializer
        extends ValueDeserializer<Pattern>
    {


        @Override
        public Pattern deserialize(JsonParser parser, DeserializationContext deserializationContext)
        {
            Pattern result = new Pattern();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.VALUE_STRING) {
                result.value = parser.readValueAs(String.class);
            } else {
                if (token == JsonToken.START_OBJECT) {
                    result.value = parser.readValueAs(PatternObject.class);
                } else {
                    deserializationContext.handleUnexpectedToken(Object.class, parser);
                }
            }
            return result;
        }

    }

}
