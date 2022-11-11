import re
from typing import List, Type, Optional

from data_processing.datapoint import CodeDataPoint
from data_processing.processors.abstract_processor import ProcessorBase


class LeadingWhitespaceRemoverBase(ProcessorBase):
    """
    Removes leading whitespaces.
    """

    __LEADING_WHITESPACE_REGEX = re.compile(r"^[\r\t ]+", re.MULTILINE)

    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        return self._remove_leading_whitespace(dp)

    @staticmethod
    def _remove_leading_whitespace(dp: CodeDataPoint) -> CodeDataPoint:
        new_content = LeadingWhitespaceRemoverBase.__LEADING_WHITESPACE_REGEX.sub(
            "", dp.content
        )
        dp.content = new_content.strip()
        return dp

    @staticmethod
    def is_deterministic() -> bool:
        return True

    @staticmethod
    def must_be_after() -> List[Type[ProcessorBase]]:
        return []

    @staticmethod
    def must_be_before() -> List[Type[ProcessorBase]]:
        return []

    @staticmethod
    def is_slow() -> bool:
        return False


class LeadingWhitespaceRemoverTrainData(LeadingWhitespaceRemoverBase):
    pass


class LeadingWhitespaceRemoverTrainModel(LeadingWhitespaceRemoverBase):
    pass


class LeadingWhitespaceRemoverInference(LeadingWhitespaceRemoverBase):
    pass
