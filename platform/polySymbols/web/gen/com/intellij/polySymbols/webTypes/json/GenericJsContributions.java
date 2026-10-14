
package com.intellij.polySymbols.webTypes.json;

import java.util.ArrayList;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = GenericJsContributions.MyDeserializer.class)
public class GenericJsContributions
    extends ArrayList<GenericJsContributionOrProperty>
{


    public static class MyDeserializer
        extends ValueDeserializer<GenericJsContributions>
    {


        @Override
        public GenericJsContributions deserialize(JsonParser parser, DeserializationContext deserializationContext)
        {
            GenericJsContributions result = new GenericJsContributions();
            JsonToken token = parser.currentToken();
            if (token == JsonToken.START_ARRAY) {
                while (parser.nextToken()!= JsonToken.END_ARRAY) {
                    token = parser.currentToken();
                    if ((((((token == JsonToken.VALUE_NUMBER_INT)||(token == JsonToken.VALUE_NUMBER_FLOAT))||(token == JsonToken.VALUE_TRUE))||(token == JsonToken.VALUE_FALSE))||(token == JsonToken.VALUE_STRING))||(token == JsonToken.START_OBJECT)) {
                        result.add(parser.readValueAs(GenericJsContributionOrProperty.class));
                    } else {
                        parser.readValueAsTree();
                    }
                }
            } else {
                if ((((((token == JsonToken.VALUE_NUMBER_INT)||(token == JsonToken.VALUE_NUMBER_FLOAT))||(token == JsonToken.VALUE_TRUE))||(token == JsonToken.VALUE_FALSE))||(token == JsonToken.VALUE_STRING))||(token == JsonToken.START_OBJECT)) {
                    result.add(parser.readValueAs(GenericJsContributionOrProperty.class));
                } else {
                    parser.readValueAsTree();
                }
            }
            return result;
        }

    }

}
