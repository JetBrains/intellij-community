import abc
from typing import List, Type, Optional

import black

from data_processing.datapoint import CodeDataPoint
from data_processing.processors.abstract_processor import ProcessorBase
from data_processing.processors.empty_line_remover import EmptyLineRemoverBase


class BlackStylerBase(ProcessorBase):
    @staticmethod
    def _format(dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        last_line_is_empty = not dp.content.split("\n")[-1].strip()

        try:
            new_content = black.format_str(dp.content, mode=black.FileMode(line_length=120, magic_trailing_comma=False))
        except (black.parsing.InvalidInput, IndentationError, KeyError):
            return None

        if not last_line_is_empty:
            assert new_content[-1] == "\n"
            new_content = new_content[:-1]

        dp.content = new_content
        return dp

    @abc.abstractmethod
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        raise NotImplementedError

    @staticmethod
    def is_deterministic() -> bool:
        return True

    @staticmethod
    def is_slow() -> bool:
        return True

    @property
    def _chunk_size(self) -> int:
        return 10

    @staticmethod
    def must_be_after() -> List[Type[ProcessorBase]]:
        return []

    @staticmethod
    def must_be_before() -> List[Type[ProcessorBase]]:
        return [EmptyLineRemoverBase]


class BlackStylerTrainData(BlackStylerBase):
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        return self._format(dp)


class BlackStylerTrainModel(BlackStylerBase):
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        return self._format(dp)


class BlackStylerInference(BlackStylerBase):
    def _process(self, dp: CodeDataPoint) -> Optional[CodeDataPoint]:
        return self._format(dp)
