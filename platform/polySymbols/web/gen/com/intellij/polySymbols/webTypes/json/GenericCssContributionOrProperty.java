
package com.intellij.polySymbols.webTypes.json;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = GenericCssContributionOrProperty.MyDeserializer.class)
public class GenericCssContributionOrProperty
    implements GenericContributionOrProperty
{

    /**
     * Type: {@code String | Double | Boolean | GenericCssContribution}
     * 
     */
    private Object value;

    /**
     * Type: {@code String | Double | Boolean | GenericCssContribution}
     * 
     */
    public Object getValue() {
        return value;
    }

    /**
     * Type: {@code String | Double | Boolean | GenericCssContribution}
     * 
     */
    public void setValue(Object value) {
        this.value = value;
    }

    public static class MyDeserializer
        extends ValueDeserializer<GenericCssContributionOrProperty>
    {


        @Override
        public GenericCssContributionOrProperty deserialize(JsonParser parser, DeserializationContext deserializationContext)
        {
            GenericCssContributionOrProperty result = new GenericCssContributionOrProperty();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.VALUE_STRING) {
                result.value = parser.readValueAs(String.class);
            } else {
                if ((token == JsonToken.VALUE_NUMBER_INT)||(token == JsonToken.VALUE_NUMBER_FLOAT)) {
                    result.value = parser.readValueAs(Double.class);
                } else {
                    if ((token == JsonToken.VALUE_TRUE)||(token == JsonToken.VALUE_FALSE)) {
                        result.value = parser.readValueAs(Boolean.class);
                    } else {
                        if (token == JsonToken.START_OBJECT) {
                            result.value = parser.readValueAs(GenericCssContribution.class);
                        } else {
                            deserializationContext.handleUnexpectedToken(Object.class, parser);
                        }
                    }
                }
            }
            return result;
        }

    }

}
