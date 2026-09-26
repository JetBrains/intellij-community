
package com.intellij.polySymbols.webTypes.json;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = NamePatternRoot.MyDeserializer.class)
public class NamePatternRoot {

    /**
     * Type: {@code NamePatternBase | String}
     * 
     */
    private Object value;

    /**
     * Type: {@code NamePatternBase | String}
     * 
     */
    public Object getValue() {
        return value;
    }

    /**
     * Type: {@code NamePatternBase | String}
     * 
     */
    public void setValue(Object value) {
        this.value = value;
    }

    public static class MyDeserializer
        extends ValueDeserializer<NamePatternRoot>
    {


        @Override
        public NamePatternRoot deserialize(JsonParser parser, DeserializationContext deserializationContext)
        {
            NamePatternRoot result = new NamePatternRoot();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.START_OBJECT) {
                result.value = parser.readValueAs(NamePatternBase.class);
            } else {
                if (token == JsonToken.VALUE_STRING) {
                    result.value = parser.readValueAs(String.class);
                } else {
                    deserializationContext.handleUnexpectedToken(Object.class, parser);
                }
            }
            return result;
        }

    }

}
