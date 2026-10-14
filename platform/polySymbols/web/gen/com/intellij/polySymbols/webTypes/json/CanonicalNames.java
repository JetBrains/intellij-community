
package com.intellij.polySymbols.webTypes.json;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = CanonicalNames.MyDeserializer.class)
public class CanonicalNames {

    /**
     * Type: {@code NameConverter | NameConversionRulesSingle}
     * 
     */
    private Object value;

    /**
     * Type: {@code NameConverter | NameConversionRulesSingle}
     * 
     */
    public Object getValue() {
        return value;
    }

    /**
     * Type: {@code NameConverter | NameConversionRulesSingle}
     * 
     */
    public void setValue(Object value) {
        this.value = value;
    }

    public static class MyDeserializer
        extends ValueDeserializer<CanonicalNames>
    {


        @Override
        public CanonicalNames deserialize(JsonParser parser, DeserializationContext deserializationContext)
        {
            CanonicalNames result = new CanonicalNames();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.VALUE_STRING) {
                result.value = parser.readValueAs(NameConversionRulesSingle.NameConverter.class);
            } else {
                if (token == JsonToken.START_OBJECT) {
                    result.value = parser.readValueAs(NameConversionRulesSingle.class);
                } else {
                    deserializationContext.handleUnexpectedToken(Object.class, parser);
                }
            }
            return result;
        }

    }

}
